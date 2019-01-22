package sam.ms;
import static sam.config.MyConfig.NEW_MANGAS_TSV_FILE;
import static sam.config.MyConfig.UPDATED_MANGAS_TSV_FILE;
import static sam.console.ANSI.createBanner;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_ID;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.Callable;

import sam.downloader.db.entities.meta.IDManga;
import sam.manga.scrapper.ScrapperException;
import sam.ms.entities.Manga;
import sam.ms.extras.Utils;
import sam.ms.scrapper.Scraps;
import sam.ms.scrapper.ScrapsListener;
import sam.tsv.Column;
import sam.tsv.Row;
import sam.tsv.Tsv;


public class TsvScrapper implements ScrapsListener, Callable<Void> {
	private final MangaList mangaList;
	private final LinkedList<Integer> ids = new LinkedList<>();
	private final int limit;
	private Integer firstNewManga;

	public TsvScrapper(MangaList mangaList, int limit) throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException, IOException, ScrapperException {
		this.mangaList = mangaList;
		this.limit = limit;
	}

	private void read(String msg, String tsvFile) throws IOException {
		Path p = Paths.get(tsvFile);
		if(Files.notExists(p)) {
			System.out.println(red(msg+": not found: ")+tsvFile);
			return;
		}
		Tsv tsv = Tsv.parse(p);
		Column limit = tsv.getColumnIfPresent("limit");
		Column mangaId = tsv.getColumn(MANGA_ID);

		for (Row r : tsv) {
			Manga m = mangaList.get(mangaId.getInt(r));

			if(m == null) {
				m = new Manga(Utils.MANGA_DIR, r, Loader.URL_COLUMN);
				mangaList.add(m);
			}

			ids.add(m.getMangaId());

			if(limit !=  null)
				m.setLimit(limit(limit.get(r)));
		}
	}

	private int limit(String s) {
		if(s == null || s.trim().isEmpty())
			return Integer.MAX_VALUE;
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			System.out.println(red("bad limit value: ")+s);
		}
		return Integer.MAX_VALUE;
	}

	private int totalCount;

	@Override
	public IDManga nextManga() {
		Integer id = ids.pollFirst();
		
		if(id == null)
			return STOP_MANGA;
		
		if(ids.size() == totalCount)
			System.out.println(createBanner("Updated Mangas"));
		if(id.equals(firstNewManga))
			System.out.println(createBanner("New Mangas"));
		
		return Objects.requireNonNull(mangaList.get(id));
	}

	@Override
	public int totalCountOfManga() {
		return totalCount;
	}
	@Override
	public int remainingCountOfManga() {
		return ids.size();
	}
	
	@Override
	public Void call() throws Exception {
		read("Updated Mangas Tsv", UPDATED_MANGAS_TSV_FILE);

		if(ids.isEmpty())
			System.out.println(red("no updates"));

		int size = ids.size();

		read("New Mangas Tsv", NEW_MANGAS_TSV_FILE);

		if(ids.size() == size)
			System.out.println(red("no new mangas"));
		else
			firstNewManga = ids.get(size);

		if(ids.isEmpty())
			System.out.println(yellow("No Data to extract"));

		mangaList.forEach(m -> {
			if(m.getLimit() == Integer.MAX_VALUE)
				m.setLimit(limit);
		});

		this.totalCount = ids.size();
		Loader.load(mangaList, Collections.emptyList(), ids);

		if(Utils.debug() || Utils.dryRun()) {
			ids.forEach(id -> {
				Manga m = mangaList.get(id);
				System.out.println(yellow(id + ", "+m.getDirName())+"\n   filter: "+(m.getFilter() == null ? "[ALL]" : m.getFilter()));
			});
			System.out.println("\n\n");
		}

		new Scraps(this).run();
		return null;	
	}
}
