package sam.ms;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static sam.api.config.InjectorKeys.APP_DATA_DIR;
import static sam.api.config.InjectorKeys.DEBUG;
import static sam.api.config.InjectorKeys.MANGA_DIR;
import static sam.api.config.InjectorKeys.SAMROCK_DB;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.manga.samrock.mangas.MangasMeta.MANGAS_TABLE_NAME;
import static sam.myutils.HPPCUtils.computeIfAbsent;
import static sam.myutils.HPPCUtils.forEach;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoublePredicate;
import java.util.function.Function;
import java.util.function.IntFunction;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import com.almworks.sqlite4java.SQLiteException;
import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntObjectScatterMap;

import sam.api.chapter.ChapterFilter;
import sam.api.config.ConfigService;
import sam.api.store.entities.meta.SChapter;
import sam.api.store.entities.meta.SManga;
import sam.api.store.entities.meta.SPage;
import sam.collection.MappedIterator;
import sam.console.ANSI;
import sam.io.serilizers.LongSerializer;
import sam.manga.Renamer;
import sam.manga.api.scrapper.ScrappedChapter;
import sam.manga.api.scrapper.ScrappedPage;
import sam.manga.api.scrapper.ScrapperException;
import sam.manga.samrock.mangas.MangasMeta;
import sam.manga.samrock.urls.nnew.MangaUrlResolver;
import sam.ms.entities.Chapter;
import sam.ms.entities.Manga;
import sam.ms.entities.Page;
import sam.ms.scrapper.Scraps;
import sam.ms.scrapper.ScrapsFactory;
import sam.myutils.Checker;
import sam.sql.QueryUtils;
import sam.sql.sqlite.Sqlite4JavaDB;
import sam.string.StringSplitIterator;
public class MangaIdChapterNumberScrapper implements ScrapsFactory {
	private final Provider<MangaList> mangasListProvider;
	private final IntArrayList mangas = new IntArrayList();
	private final boolean debug;
	private final Path appData;
	private final Path dbPath;
	private final Path mangaRootDir;
	private final ConfigService configService;
	private final int limit;
	private final IntObjectScatterMap<DoublePredicate> filters = new IntObjectScatterMap<>();
	private final AtomicInteger chapterId = new AtomicInteger();
	private final AtomicInteger pageId = new AtomicInteger();
	private final Provider<Scraps> scrapsProvider;
	private final PrintStream out;
	
	private MangaList mangasList;

	@Inject
	public MangaIdChapterNumberScrapper(
			final Provider<MangaList> mangas, 
			Provider<Scraps> scraps, 
			@Named(DEBUG) boolean debug, 
			@Named(APP_DATA_DIR) Path appData, 
			@Named(SAMROCK_DB) Path dbPath,
			@Named(MANGA_DIR) Path mangaRootDir,
			ConfigService configService,
			PrintStream out
			) {
		this.out = out;
		this.mangasListProvider = mangas;
		this.scrapsProvider = scraps;
		this.debug = debug;
		this.appData = appData;
		this.dbPath = dbPath;
		this.configService = configService;
		this.mangaRootDir = mangaRootDir;
		this.limit = configService.getInt("mc.limit", Integer.MAX_VALUE);
	}
	public void start(final List<String> args) throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException, SQLiteException, ScrapperException {
		if(args.isEmpty()) {
			out.println(red("no data input"));
			return;
		}

		Path p1 = appData.resolve("-mc.log");
		Path p2 = appData.resolve("last-mc");
		
		parseArgs(args, mangas, filters, debug ? out : null);

		if(mangas.isEmpty()) {
			out.println(red("no manga id(s) found"));
			return;
		}

		if(Files.exists(p1)) {
			StringBuilder sb = new StringBuilder();
			forEach(mangas, mangaId -> sb.append(" ").append(mangaId).append("  ").append(filters.get(mangaId)));
			sb.append('\n');

			byte[] b1 = sb.toString().getBytes();
			byte[] b2 = Files.readAllBytes(p2);

			if(!Arrays.equals(b1, b2)) {
				Files.write(p2, b1, TRUNCATE_EXISTING);
				Path temp = p1.resolveSibling("temp");
				OutputStream os = Files.newOutputStream(temp, CREATE, TRUNCATE_EXISTING);

				sb.insert(0, "[" + LocalDateTime.now() + "]");

				os.write(sb.toString().getBytes());
				Files.copy(p1, os);
				os.flush();
				os.close();
				Files.delete(p1);
				Files.move(temp, p1);
				out.println(yellow("-mc.log saved"));
			}
		}

		String[] cols = urlColumns();
		mangasList = mangasListProvider.get();
		int[] max = {0,0};
		mangasList.forEach(m -> m.forEach(mc -> {
			Chapter c = (Chapter)mc; 
			max[0] = Math.max(max[0], c.getId());
			c.forEach(p -> max[1] = Math.max(max[1], p.getId()));
		}));
		
		chapterId.set(max[0]);
		pageId.set(max[1]);
		
		updateMangaList(cols);
		
		if(mangas.isEmpty()) {
			out.println(red("no manga id(s) found to process"));
			return;
		}
		
		Scraps scraps = scrapsProvider.get();
		scraps.start(this, () -> mangaIterator(), mangas.size());
	}

	private String[] urlColumns() {
		String key = "mc.urls.columns";
		String urlColumnsString = configService.get(key);
		List<String> cols = new ArrayList<>();
		
		new StringSplitIterator(Objects.requireNonNull(urlColumnsString, "\""+key+"\" not found in config"), ',')
		.forEachRemaining(s -> {
			s = s.trim();
			if(s.indexOf(' ') >= 0)
				throw new IllegalArgumentException("bad url column: \""+s+"\", in: \""+urlColumnsString+"\"");
			
			cols.add(s);
		});
		
		if(cols.isEmpty()) 
			throw new IllegalStateException("in url columns in \""+key+"\"");
		
		return cols.toArray(new String[cols.size()]);
	}
	private void updateMangaList(String[] urlColumns) throws IOException, SQLiteException {
		IntArrayList missing = new IntArrayList(mangas);
		missing.removeAll(id -> mangasList.get(id) != null);
		if(debug)
			out.println("updating manga-ids: "+missing);
		
		Path lastModFile = appData.resolve("mc.db.last.modified");
		
		long lastMod = -1;
		if(missing.isEmpty()) 
			lastMod = Files.notExists(lastModFile) ? -1 : new LongSerializer().read(lastModFile);
		
		if(lastMod == dbPath.toFile().lastModified())
			return;
			
		try(Sqlite4JavaDB db = new Sqlite4JavaDB(dbPath, false)) {
			if(!missing.isEmpty()) {
				StringBuilder sb = QueryUtils.selectSQL(MANGAS_TABLE_NAME, Manga.dbFields()).append(" WHERE ").append(MangasMeta.MANGA_ID).append(" IN(");
				forEach(missing, s -> sb.append(s).append(','));
				sb.setCharAt(sb.length() - 1, ')');
				sb.append(';');
				db.iterate(sb.toString(), rs -> mangasList.add(new Manga(rs)));
			}
			
			IntFunction<ChapterFilter> computer = id -> new ChapterFilter(id);
			IntObjectScatterMap<ChapterFilter> chapFilters = new IntObjectScatterMap<>();
			
			String sql = "SELECT manga_id, number FROM Chapters WHERE manga_id IN("+QueryUtils.join(mangas.toArray())+");";
			db.iterate(sql, rs -> {
				computeIfAbsent(chapFilters, rs.columnInt(0), computer)
				.add(rs.columnDouble(1));
			});
			
			forEach(chapFilters, (s,t) -> t.setCompleted());
			forEach(mangas, manga_id -> {
				Filter2 f = (Filter2) filters.get(manga_id);
				DoublePredicate df = f == null ? null : f.predicate; 
				ChapterFilter cf = chapFilters.get(manga_id);
				
				if(df == null && cf == null)
					filters.remove(manga_id);
				else if(cf == null)
					filters.put(manga_id, f);
				else if(df == null)
					filters.put(manga_id, cf.negate());
				else {
					filters.put(manga_id, f.and(cf.negate()));
				}
			});
			
			Map<String, MangaUrlResolver> map = MangaUrlResolver.get(db, urlColumns);
			@SuppressWarnings("unchecked")
			Function<String, String>[] resolvers = new Function[urlColumns.length];
			
			for (int i = 0; i < urlColumns.length; i++) {
				resolvers[i] = map.get(urlColumns[i]);
				if(resolvers[i] == null) {
					if(debug) 
						out.println(red("no resolver found for: ")+urlColumns[i]);	
					resolvers[i] = Function.identity();
				}
			}
			
			db.iterate("SELECT manga_id, "+String.join(",", urlColumns)+" FROM MangaUrls WHERE manga_id IN("+QueryUtils.join(mangas.toArray())+")", rs -> {
				Manga manga = mangasList.get(rs.columnInt(0));
				if(manga == null) {
					out.println(red("no manga found for manga_id: ")+rs.columnString(0));
					return;
				}
				
				String[] urls = new String[urlColumns.length];
				int nullCount = 0;
				
				for (int i = 0; i < urlColumns.length; i++) {
					String part = rs.columnString(i + 1);
					if(Checker.isEmptyTrimmed(part))
						nullCount++;
					else
						urls[i] = resolvers[i].apply(part);	
				}
				
				if(nullCount == urls.length) {
					out.println(ANSI.red("no urls found for: ")+manga.getMangaId()+": "+manga.getMangaName());
					mangas.remove(Integer.valueOf(manga.getMangaId()));
				} else {
					manga.setUrls(urls);
				}
			});
		}
	}
	
	public static void parseArgs(List<String> args, IntArrayList mangasIds, IntObjectScatterMap<DoublePredicate> filters, PrintStream out) {
		Filter2 current = null;
		IntFunction<Filter2> computer = id -> {
			mangasIds.add(id);
			return new Filter2(id);
		};

		if(args.size() == 1) {
			int id = Integer.parseInt(args.get(0));
			filters.put(id, new Filter2(id));
			mangasIds.add(id);
		} else {
			for (String s : args) {
				if(s.indexOf('_') < 0 && 
						s.indexOf('-') < 0 && 
						s.indexOf('.') < 0 && 
						s.length() > 3) {
					current = (Filter2) computeIfAbsent(filters, Integer.parseInt(s), computer);
				} else if(current != null) {
					current.add(s);					
				}
			}
		}
	}
	
	private Iterator<SManga> mangaIterator() {
		return new MappedIterator<>(mangas.iterator(), id -> mangasList.get(id.value));
	}
	@Override
	public SChapter getChapter(SPage page) {
		return ((Page)page).getChapter();
	}
	@Override
	public SManga getManga(SChapter c) {
		return ((Chapter)c).getManga();
	}

	@Override
	public DoublePredicate filterFor(SManga manga) {
		return filters.get(manga.getMangaId());
	}
	@Override
	public int limitFor(SManga manga) {
		// TODO implement way to set limit per manga  
		return limit;
	}
	@Override
	public SChapter newChapter(SManga manga, ScrappedChapter sc) {
		return new Chapter((Manga)manga, sc, chapterId.incrementAndGet());
	}
	@Override
	public SPage newPage(SChapter chapter, ScrappedPage sc) {
		return new Page((Chapter)chapter, pageId.incrementAndGet(), sc.getOrder(), sc.getPageUrl(), sc.getImgUrl());
	}
	
	@Override
	public Function<SChapter, Path> chapterPathResolver(SManga manga) {
		Path mangaPath = mangaRootDir.resolve(manga.getDirName());
		String mangaName = manga.getMangaName();
		return chap -> mangaPath.resolve(Renamer.makeChapterFileName(chap.getNumber(), chap.getTitle(), mangaName));
	}
}
