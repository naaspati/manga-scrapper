import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import mangafoxscrapper.scrapper.Scrapper;
import mangafoxscrapper.scrapper.Scraps;
import sam.config.MyConfig;
import sam.manga.newsamrock.SamrockDB;
import sam.manga.newsamrock.mangas.MangasMeta;
import sam.manga.scrapper.extras.Utils;
import sam.manga.scrapper.manga.parts.ChapterFilter;
import sam.manga.scrapper.manga.parts.Manga;
import sam.tsv.Tsv;

public class MangaIdChapterNumberScrapper {
	private final Map<Integer, Manga> backupMangasMap;
	private Scraps scraps;
	private final List<String> data;

	public MangaIdChapterNumberScrapper(final List<String> data, final Map<Integer, Manga> backupMangasMap) throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException{
		this.backupMangasMap = backupMangasMap;
		this.data = data;
	}
	public void start() throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException, SQLException {
		start(-1);
	}
	public void start(final int mangasize) throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		if(data.isEmpty()){
			System.out.println(red("no data input"));
			return;
		}
		final Map<Integer, ChapterFilter> mangaIdChapFltrMap = parseData(data);

		if(mangaIdChapFltrMap.isEmpty()){
			System.out.println(red("no manga id(s) found"));
			return;
		}
		Map<Integer, Manga> mangaMap = prepareMangaMap(mangaIdChapFltrMap);
		List<Integer> failed = new ArrayList<>();

		mangaIdChapFltrMap.forEach((id, missings) -> {
			Manga m = backupMangasMap.get(id);

			if(m == null){
				System.out.println(red("no manga data with id: "+id)); 
				return;
			}
			if(m.dirName == null || m.url == null){
				System.out.println(red(m));
				failed.add(id);
			}
			else
				System.out.println(yellow(id + ", "+m.dirName)+"\n   missings: "+(Utils.isPrintFilter() ? "" : missings));
		});
		Path p1 = Main.APP_HOME.resolve("-mc.log");
		Path p2 = Main.APP_HOME.resolve("last-mc");

		if(Files.exists(p1)) {
			StringBuilder sb = new StringBuilder();
			mangaIdChapFltrMap.forEach((id, missings) -> sb.append(" ").append(id).append("  ").append(missings));
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
		if(!failed.isEmpty()){
			System.out.println(red("\n\nbad data -> ")+failed);
			mangaIdChapFltrMap.keySet().removeAll(failed);
			if(mangaIdChapFltrMap.isEmpty()) {
				System.out.println(red("ALL Bad Data"));
				return;
			}
		}
		this.scraps = new Scraps(scrapper(), mangaMap.values(), Integer.MAX_VALUE, mangasize < 0 ? mangaMap.size() : mangasize);
		scraps.scrap(Paths.get(MyConfig.MANGA_DIR));
	}
	private Scrapper scrapper0;
	public Scrapper scrapper() {
		if(scrapper0 == null) {
			try {
				scrapper0 = new Scrapper();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return scrapper0;
	}
	private Map<Integer, Manga> prepareMangaMap(Map<Integer, ChapterFilter> srcMangaIdChapFltrMap) throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException, IOException {
		Map<Integer, Manga> mangasMap = new LinkedHashMap<>();
		List<Integer> missingMangas = new ArrayList<>();
		List<Integer> missingChapterMangas = new ArrayList<>();

		srcMangaIdChapFltrMap.forEach((mangaId, filter) -> {
			Manga manga = backupMangasMap.get(mangaId);
			if(manga != null) {
				mangasMap.put(mangaId, manga);
				if(filter.hasTester())
					manga.setFilter(filter);
			}
			else 
				missingMangas.add(mangaId);

			if(!filter.hasTester())
				missingChapterMangas.add(mangaId);
		});
		if(missingMangas.isEmpty() && missingChapterMangas.isEmpty()) 
			return mangasMap;

		try(SamrockDB  db = new SamrockDB()) {
			Map<Integer, String> mangaurls = missingMangas.isEmpty() ? new HashMap<>() : db.url().getUrls(missingMangas, scrapper().urlColumn());

			if(mangaurls.values().stream().anyMatch(Objects::isNull)) {
				System.out.println("column-name: "+scrapper().urlColumn());

				Tsv t = new Tsv(MangasMeta.MANGA_ID, MangasMeta.MANGA_NAME, scrapper().urlColumn());

				mangaurls.values().removeIf(Objects::nonNull);

				db.manga().select(mangaurls.keySet(), 
						rs -> t.addRow(rs.getString(MangasMeta.MANGA_ID), rs.getString(MangasMeta.MANGA_NAME)), 
						MangasMeta.MANGA_ID, MangasMeta.MANGA_NAME);

				System.out.println(red("\nmissing urls"));
				System.out.print(String.format(yellow("%-10s%-10s%n"), "manga_id", "manga_name"));
				t.forEach(r -> System.out.printf("%-10s%-10s%n", r.get(0), r.get(1)));
				t.save(Paths.get("missing-urls.tsv"));

				System.out.println("\nmissing-urls.tsv created");

				System.exit(0);
			}
			if(!missingMangas.isEmpty()) {
				db.manga().select(missingMangas, rs -> {
					int id = rs.getInt(MangasMeta.MANGA_ID);
					Manga manga = new Manga(rs, mangaurls.get(id));
					mangasMap.put(manga.id, manga);
					backupMangasMap.put(manga.id, manga);
				}, MangasMeta.MANGA_ID, MangasMeta.DIR_NAME, MangasMeta.MANGA_NAME);                
			}
			srcMangaIdChapFltrMap.putAll(scrapper().getMissingsFilters(missingChapterMangas, db));
		}
		mangasMap.forEach((id, manga) -> manga.setFilter(srcMangaIdChapFltrMap.get(id)));

		return mangasMap;
	}

	public Scraps getScraps() {
		return scraps;
	}
	
	private Map<Integer, ChapterFilter> parseData(List<String> data) {
		Map<Integer, ChapterFilter> map = new LinkedHashMap<>();
		ChapterFilter current = null;
		Function<Integer, ChapterFilter> computer = i -> new ChapterFilter();

		if(data.size() == 1)
			map.put(Integer.parseInt(data.get(0)), new ChapterFilter());
		else {
			for (String s : data) {
				if(s.indexOf('_') < 0 && 
						s.indexOf('-') < 0 && 
						s.indexOf('.') < 0 && 
						s.length() > 3){
					current = map.computeIfAbsent(Integer.parseInt(s), computer);
				} else if(current != null)
					current.add(s);
			}
		}
		return map;
	}
}
