package sam.ms;

import static sam.console.ANSI.yellow;
import static sam.manga.samrock.mangas.MangasMeta.DIR_NAME;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_ID;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_NAME;

import java.io.IOException;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import sam.logging.MyLoggerFactory;
import sam.manga.samrock.SamrockDB;
import sam.manga.samrock.chapters.ChapterFilter;
import sam.manga.samrock.chapters.ChapterFilterUtils;
import sam.manga.samrock.chapters.ChapterUtils;
import sam.manga.samrock.mangas.MangaUtils;
import sam.manga.samrock.urls.MangaUrlsMeta;
import sam.manga.samrock.urls.MangaUrlsUtils;
import sam.ms.entities.Manga;
import sam.ms.extras.Utils;
import sam.myutils.System2;
import sam.string.StringBuilder2;
import sam.tsv.Tsv;

public class Loader implements AutoCloseable {
	private final SamrockDB  db;
	private static final Logger LOGGER = MyLoggerFactory.logger(Loader.class);

	public Loader() throws SQLException {
		this.db = new SamrockDB();
	}

	public Map<Integer, Manga> loadMangas(String url_column, Collection<Integer> mangaIds) throws SQLException {
		if(mangaIds.isEmpty())
			return Collections.emptyMap();

		Map<Integer, Manga> map = new HashMap<>();

		MangaUtils mangas = new MangaUtils(db);
		MangaUrlsUtils urls = new MangaUrlsUtils(db);
		Map<Integer, String> mangaurls = urls.getUrls(mangaIds, url_column);

		if(mangaurls.values().stream().anyMatch(Objects::isNull)) {
			System.out.println("column-name: "+url_column);

			Tsv t = new Tsv(MANGA_ID, MANGA_NAME, url_column);

			mangaurls.values().removeIf(Objects::nonNull);

			mangas.select(mangaurls.keySet(), 
					rs -> t.addRow(rs.getString(MANGA_ID), rs.getString(MANGA_NAME)), 
					MANGA_ID, MANGA_NAME);

			StringBuilder2 sb = new StringBuilder2();
			sb.red("\nmissing urls\n");
			sb.format(yellow("%-10s%-10s%n"), "manga_id", "manga_name");
			t.forEach(r -> sb.format("%-10s%-10s%n", r.get(0), r.get(1)));

			try {
				t.save(Paths.get("missing-urls.tsv"));
			} catch (IOException e) {
				throw new SQLException(sb.toString(), e);
			}

			sb.append("\nmissing-urls.tsv created");

			throw new SQLException(sb.toString());
		}

		mangas.select(mangaIds, rs -> {
			int id = rs.getInt(MANGA_ID);
			Manga manga = new Manga(Utils.MANGA_DIR, rs, mangaurls.get(id));
			map.put(manga.getMangaId(), manga);
		}, MANGA_ID, DIR_NAME, MANGA_NAME);

		return map;
	}

	public Map<Integer, ChapterFilter> loadChapterFilters(Collection<Integer> mangaIds, String filterTitle) throws SQLException {
		return new ChapterUtils(db).getChapterFilters(mangaIds, filterTitle);
	}

	@Override
	public void close() throws SQLException {
		if(db != null)
			db.close();
	}
	
	public static final String URL_COLUMN = Optional.ofNullable(System2.lookup("url_column")).orElse(MangaUrlsMeta.MANGAHERE);

	public static void load(MangaList mangasList, Collection<Integer> loadMangas, Collection<Integer> loadFilters) throws SQLException, IOException {
		try(Loader loader = new Loader()) {
			// load missing mangas 
			if(!loadMangas.isEmpty()) {
				LOGGER.fine(() -> "manga_ids to load: "+loadMangas);
				Map<Integer, Manga> map = loader.loadMangas(URL_COLUMN, loadMangas);
				
				if(loadMangas.size() != map.size()) 
					throw new SQLException(loadMangas.stream().filter(id -> !map.containsKey(id)).map(i -> i.toString()).collect(Collectors.joining(",",  "mangas not found for id(s): [", "]")));
				map.forEach((s,manga) -> mangasList.add(manga));
			}
			
			// load missing filter
			Map<Integer, ChapterFilter> filters = loader.loadChapterFilters(loadFilters, Utils.debug() ? "Except" : null);
			filters.forEach((id, filter) -> mangasList.get(id).setFilter(ChapterFilterUtils.invertFilter("Except ", filter)));
		}
	}
}
