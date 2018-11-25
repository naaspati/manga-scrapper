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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.Callable;

import sam.downloader.db.entities.meta.IDManga;
import sam.ms.entities.Manga;
import sam.ms.scrapper.Scrapper;
import sam.ms.scrapper.Scraps;
import sam.ms.scrapper.ScrapsListener;
import sam.tsv.Column;
import sam.tsv.Row;
import sam.tsv.Tsv;


public class TsvScrapper implements ScrapsListener, Callable<Void> {
	private final MangaList mangaList;
	private LinkedList<Manga> updateIds, newIds;
	private final int limit;

	public TsvScrapper(MangaList mangaList, int limit) throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException, IOException {
		this.mangaList = mangaList;
		this.limit = limit;
	}

	private LinkedList<Manga> read(String msg, String tsvFile) throws IOException {
		LinkedList<Manga> set = new LinkedList<>();

		Path p = Paths.get(tsvFile);
		if(Files.notExists(p)) {
			System.out.println(red(msg+": not found: ")+tsvFile);
			return set;
		}
		Tsv tsv = Tsv.parse(p);
		Column limit = tsv.getColumnIfPresent("limit");
		Column mangaId = tsv.getColumn(MANGA_ID);

		for (Row r : tsv) {
			Manga m = mangaList.get(mangaId.getInt(r));

			if(m == null) {
				m = new Manga(r, sam.manga.scrapper.Scrapper.urlColumn());
				mangaList.add(m);
			}
			set.add(m);
			if(limit !=  null)
				m.setLimit(limit(limit.get(r)));
		}
		return set;
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

	private boolean updated = true, nnew = true;
	private int totalCount;

	@Override
	public IDManga nextManga() {
		if(!updateIds.isEmpty()) {
			if(updated) {
				updated = false;
				System.out.println(createBanner("Updated Mangas"));
			}
			return updateIds.removeFirst();
		}
		if(!newIds.isEmpty()) {
			if(nnew) {
				nnew = false;
				System.out.println(createBanner("New Mangas"));
			}
			return newIds.removeFirst();
		}
		return STOP_MANGA;
	}

	@Override
	public int totalCountOfManga() {
		return totalCount;
	}
	@Override
	public int remainingCountOfManga() {
		return updateIds.size()+newIds.size();
	}
	

	@Override
	public Void call() throws Exception {
		this.updateIds = read("Updated Mangas Tsv", UPDATED_MANGAS_TSV_FILE);
		this.newIds = read("New Mangas Tsv", NEW_MANGAS_TSV_FILE);


		if(updateIds.isEmpty())
			System.out.println(red("no updates"));
		if(newIds.isEmpty())
			System.out.println(red("no new mangas"));

		if(newIds.isEmpty() && updateIds.isEmpty())
			System.out.println(yellow("No Data to extract"));

		mangaList.forEach(m -> {
			if(m.getLimit() == Integer.MAX_VALUE)
				m.setLimit(limit);
		});

		this.totalCount = updateIds.size()+ newIds.size();
		
		new FilterLoader().load(mangaList, new HashMap<>());
		new Scraps(new Scrapper(), this).run();
		return null;	
	}
}
