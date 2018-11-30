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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import sam.downloader.db.entities.meta.IDManga;
import sam.ms.entities.ChapterFilter;
import sam.ms.entities.Manga;
import sam.ms.extras.Utils;
import sam.ms.scrapper.Scrapper;
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
	public void start() throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
		if(data.isEmpty()){
			System.out.println(red("no data input"));
			return;
		}
		final LinkedHashMap<Integer, ChapterFilter> chapterFilters = parseData(data);

		if(chapterFilters.isEmpty()){
			System.out.println(red("no manga id(s) found"));
			return;
		}
		new FilterLoader().load(mangasList, Collections.unmodifiableMap(chapterFilters));
		List<Integer> failed = new ArrayList<>();
		chapterFilters.forEach((id, missings) -> {
			Manga m = mangasList.get(id);

			if(m == null){
				System.out.println(red("no manga data with id: "+id));
				failed.add(id);
				return;
			}
			if(m.getDirName() == null || m.getUrl() == null){
				System.out.println(red(m));
				failed.add(id);
			}
			else
				System.out.println(yellow(id + ", "+m.getDirName())+"\n   missings: "+(Utils.isPrintFilter() ? "" : missings));
		});
		
		Path p1 = Utils.APP_DATA.resolve("-mc.log");
		Path p2 = Utils.APP_DATA.resolve("last-mc");

		if(Files.exists(p1)) {
			StringBuilder sb = new StringBuilder();
			chapterFilters.forEach((id, missings) -> sb.append(" ").append(id).append("  ").append(missings));
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
			chapterFilters.keySet().removeAll(failed);
			if(chapterFilters.isEmpty()) {
				System.out.println(red("ALL Bad Data"));
				return;
			}
		}
		this.mangas =  new LinkedList<>();
		chapterFilters.forEach((id,missings) -> mangas.add(Objects.requireNonNull(mangasList.get(id))));
		
		this.manga_size = mangas.size();
		Scraps scraps = new Scraps(scrapper(), this);
		scraps.run();
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
	
	private LinkedHashMap<Integer, ChapterFilter> parseData(List<String> data) {
		LinkedHashMap<Integer, ChapterFilter> map = new LinkedHashMap<>();
		
		ChapterFilter current = null;
		Function<Integer, ChapterFilter> computer = i -> new ChapterFilter(i);

		if(data.size() == 1) {
			int id = Integer.parseInt(data.get(0));
			map.put(id, new ChapterFilter(id));
		} else {
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
}
