package sam.ms;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.function.DoublePredicate;
import java.util.stream.Collectors;

import sam.downloader.db.entities.meta.IDManga;
import sam.manga.samrock.chapters.ChapterFilterBase;
import sam.manga.samrock.chapters.ChapterFilterUtils;
import sam.manga.scrapper.ScrapperException;
import sam.manga.scrapper.scrappers.impl.ScrapperCached;
import sam.ms.entities.Manga;
import sam.ms.extras.Utils;
import sam.ms.scrapper.Scraps;
import sam.ms.scrapper.ScrapsListener;


public class MangaIdChapterNumberScrapper implements ScrapsListener {
	private final MangaList mangasList;
	private LinkedList<Manga> mangas;
	private final List<String> data;
	private int manga_size;

	public MangaIdChapterNumberScrapper(final List<String> data, final MangaList mangas) throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException{
		this.mangasList = mangas;
		this.data = data;
	}
	public void start() throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException, ScrapperException {
		if(data.isEmpty()){
			System.out.println(red("no data input"));
			return;
		}
		final LinkedHashMap<Integer, Filter2> filters = parseData(data);

		if(filters.isEmpty()){
			System.out.println(red("no manga id(s) found"));
			return;
		}
		
		ScrapperCached scrapper = ScrapperCached.createDefaultInstance();
		
		Loader.load(
				scrapper.urlColumn(),
				mangasList,
				filters.keySet().stream().filter(id -> mangasList.get(id) == null).collect(Collectors.toList()), 
				filters.entrySet().stream().filter(e -> filter2(e.getValue()).predicate == null).map(e -> e.getKey()).collect(Collectors.toList())
				);
		
		this.mangas = new LinkedList<>();
		
		filters.forEach((id, filter) -> {
			Manga m = mangasList.get(id);
			
			if(filter2(filter).predicate == null) {
				if(m.getFilter() == null)
					m.setFilter(ChapterFilterUtils.ALL_ACCEPT_FILTER);
			} else {
				m.setFilter(filter);
			}
			
			if(m.getDirName() == null || m.getUrl() == null){
				System.out.println(red(m));
			} else {
				System.out.println(yellow(id + ", "+m.getDirName())+"\n   filter: "+filter);
				mangas.add(m);
			}
		});

		Path p1 = Utils.APP_DATA.resolve("-mc.log");
		Path p2 = Utils.APP_DATA.resolve("last-mc");

		if(Files.exists(p1)) {
			StringBuilder sb = new StringBuilder();
			mangas.forEach(manga -> sb.append(" ").append(manga.getMangaId()).append("  ").append(manga.getFilter()));
			sb.append('\n');

			byte[] b1 = sb.toString().getBytes();
			byte[] b2 = Files.readAllBytes(p2);

			if(!Arrays.equals(b1, b2)) {
				Files.write(p2, b1, StandardOpenOption.TRUNCATE_EXISTING);
				Path temp = p1.resolveSibling("temp");
				OutputStream os = Files.newOutputStream(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

				sb.insert(0, ']');
				sb.insert(0, LocalDateTime.now());
				sb.insert(0, '[');

				os.write(sb.toString().getBytes());
				Files.copy(p1, os);
				os.flush();
				os.close();
				Files.delete(p1);
				Files.move(temp, p1);
				System.out.println(yellow("-mc.log saved"));
			}
		}
		
		this.manga_size = mangas.size();
		Scraps scraps = new Scraps(scrapper, this);
		scraps.run();
	}
	private Filter2 filter2(Object value) {
		return (Filter2)value;
	}
	private LinkedHashMap<Integer, Filter2> parseData(List<String> data) {
		LinkedHashMap<Integer, Filter2> filters = new LinkedHashMap<>();

		Filter2 current = null;

		if(data.size() == 1) {
			int id = Integer.parseInt(data.get(0));
			filters.put(id, new Filter2(id));
		} else {
			for (String s : data) {
				if(s.indexOf('_') < 0 && 
						s.indexOf('-') < 0 && 
						s.indexOf('.') < 0 && 
						s.length() > 3){
					current = filters.computeIfAbsent(Integer.parseInt(s), Filter2::new);
				} else if(current != null)
					current.add(s);
			}
		}
		filters.forEach((s,t) -> t.setCompleted());
		return filters;
	}
	@Override
	public IDManga nextManga() {
		Manga manga = mangas.pollFirst();
		return manga == null ? STOP_MANGA : manga;
	}
	@Override
	public int totalCountOfManga() {
		return manga_size;
	}
	@Override
	public int remainingCountOfManga() {
		return mangas.size();
	}
	private static class Filter2 extends ChapterFilterBase {
		private DoublePredicate predicate;

		public Filter2(int manga_id) {
			super(manga_id);
		}
		public void add(String s) {
			check();

			DoublePredicate dp = make(s);
			predicate = predicate == null ? dp : predicate.or(dp);
			append(s);
		}
		
		@Override
		public boolean test(double value) {
			if(!complete)
				throw new IllegalStateException("not completed");

			return predicate.test(value);
		}

		private DoublePredicate make(String s) {
			int index1 = s.indexOf('-');
			int index2 = s.indexOf('_');

			if(index1 < 0 && index2 < 0){
				double r = parse(s); 
				return (d -> d == r);
			}

			if(index1 == 0 || index2 == 0){
				double r = parse(s.substring(1));

				return  index1 == 0 
						? (d -> d <= r) 
								: (d -> d < r);
			}
			else if(index1 == s.length() - 1 || index2 == s.length() - 1){
				double r = parse(s.substring(0, s.length() - 1));

				return index1 == s.length() - 1  
						? (d -> d >= r) 
								: (d -> d > r);
			}
			else{
				double n1 = parse(s.substring(0, index1 > 0 ? index1 : index2));
				double n2 = parse(s.substring((index1 > 0 ? index1 : index2) + 1, s.length()));

				return index1 > 0 
						? (d -> n1 <= d && d <= n2)
								: (d -> n1 < d && d < n2);
			}
		}
		private double parse(String s) {
			return Double.parseDouble(s);
		}
	}

}
