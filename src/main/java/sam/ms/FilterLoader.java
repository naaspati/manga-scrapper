package sam.ms;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.manga.samrock.mangas.MangasMeta.DIR_NAME;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_ID;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_NAME;
import static sam.ms.scrapper.Scrapper.URL_COLUMN;

import java.io.IOException;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import sam.manga.samrock.SamrockDB;
import sam.manga.samrock.chapters.ChapterUtils;
import sam.manga.samrock.mangas.MangaUtils;
import sam.manga.samrock.urls.MangaUrlsUtils;
import sam.ms.entities.ChapterFilter;
import sam.ms.entities.Manga;
import sam.ms.extras.Utils;
import sam.tsv.Tsv;

public class FilterLoader {
	public void load(MangaList mangasList, Map<Integer, ChapterFilter> filters) throws IOException, SQLException {
		Set<Integer> missingMangas = new HashSet<>();
		Set<Integer> missingChapterMangas = new HashSet<>();
		filters = new HashMap<>(filters);

		filters.forEach((mangaId, filter) -> {
			Manga manga = mangasList.get(mangaId);
			if(manga != null) {
				if(filter.hasTester())
					manga.setFilter(filter);
			}
			else 
				missingMangas.add(mangaId);

			if(!filter.hasTester())
				missingChapterMangas.add(mangaId);
		});
		if(missingMangas.isEmpty() && missingChapterMangas.isEmpty()) 
			return;

		try(SamrockDB  db = new SamrockDB()) {
			MangaUtils mangas = new MangaUtils(db);
			MangaUrlsUtils urls = new MangaUrlsUtils(db);
			
			Map<Integer, String> mangaurls = missingMangas.isEmpty() ? new HashMap<>() : urls.getUrls(missingMangas, URL_COLUMN);

			if(mangaurls.values().stream().anyMatch(Objects::isNull)) {
				System.out.println("column-name: "+URL_COLUMN);

				Tsv t = new Tsv(MANGA_ID, MANGA_NAME, URL_COLUMN);

				mangaurls.values().removeIf(Objects::nonNull);

				mangas.select(mangaurls.keySet(), 
						rs -> t.addRow(rs.getString(MANGA_ID), rs.getString(MANGA_NAME)), 
						MANGA_ID, MANGA_NAME);

				System.out.println(red("\nmissing urls"));
				System.out.print(String.format(yellow("%-10s%-10s%n"), "manga_id", "manga_name"));
				t.forEach(r -> System.out.printf("%-10s%-10s%n", r.get(0), r.get(1)));
				t.save(Paths.get("missing-urls.tsv"));

				System.out.println("\nmissing-urls.tsv created");

				System.exit(0);
			}
			if(!missingMangas.isEmpty()) {
				mangas.select(missingMangas, rs -> {
					int id = rs.getInt(MANGA_ID);
					Manga manga = new Manga(Utils.MANGA_DIR, rs, mangaurls.get(id));
					mangasList.add(manga);
				}, MANGA_ID, DIR_NAME, MANGA_NAME);                
			}
			getMissingsFilters(missingChapterMangas, db, filters);
		}
		Map<Integer, ChapterFilter> filters2 = filters;
		mangasList.forEach(manga -> manga.setFilter(filters2.get(manga.getMangaId())));
	}
	public Map<Integer, ChapterFilter> getMissingsFilters(Collection<Integer> mangaIds, SamrockDB db, Map<Integer, ChapterFilter> map) throws SQLException {
		new ChapterUtils(db)
		.chapterNumbers().byMangaIds(mangaIds)
		.forEach((id, chaps) -> map.put(id, new ChapterFilter(id, chaps)));

		return map;
	}
}
