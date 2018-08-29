package mangafoxscrapper.scrapper;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sam.internetutils.InternetUtils;
import sam.manga.newsamrock.SamrockDB;
import sam.manga.scrapper.manga.parts.ChapterFilter;
import sam.manga.scrapper.scrappers.impl.ScrapperCached;

public class Scrapper extends ScrapperCached {
	public Scrapper() throws IOException {
		super();
	}
	public Map<Integer, ChapterFilter> getMissingsFilters(List<Integer> mangaIds, SamrockDB db) throws SQLException {
		Map<Integer, ChapterFilter> map = new HashMap<>();

		db.chapter()
		.chapterNumbers().byMangaIds(mangaIds)
		.forEach((id, chaps) -> map.put(id, new ChapterFilter(chaps)));

		return map;
	}
	public InternetUtils internetUtils() {
		return  new InternetUtils(true);
	}
}
