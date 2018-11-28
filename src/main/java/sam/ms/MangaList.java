package sam.ms;

import java.io.IOException;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import sam.downloader.db.DownloaderDB;
import sam.downloader.db.DownloaderDBFactory;
import sam.downloader.db.entities.meta.DStatus;
import sam.downloader.db.entities.meta.IDChapter;
import sam.downloader.db.entities.meta.IDManga;
import sam.ms.entities.Chapter;
import sam.ms.entities.Manga;
import sam.ms.entities.Page;

public class MangaList implements Iterable<Manga> {

	private static volatile MangaList INSTANCE;
	public static MangaList createInstance() throws SQLException {
		if(INSTANCE == null)
			INSTANCE = new MangaList();
		return INSTANCE;
	}
	public static MangaList getInstance() {
		return INSTANCE;
	}
	private final List<Manga> mangas;
	private final Map<Integer, Manga> mangasMap = new HashMap<>();

	private MangaList() throws SQLException {
		DownloaderDB db = new DownloaderDB(Paths.get("download.db"));
		mangas = db.read(new DownloaderFactory());
		mangas.forEach(m -> mangasMap.put(m.getMangaId(), m));

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				db.save(mangas);
				System.out.println("saved: download.db");
			} catch (SQLException | IOException e) {
				e.printStackTrace();
			}
		}));
	}
	public Manga get(int manga_id) {
		return mangasMap.get(manga_id);
	}
	public void add(Manga m) {
		Objects.requireNonNull(m);
		if(!mangasMap.containsKey(m.getMangaId())) {
			mangasMap.put(m.getMangaId(), m);
			mangas.add(m);
		}
	}
	@Override
	public void forEach(Consumer<? super Manga> action) {
		mangas.forEach(action);
	}
	@Override
	public Iterator<Manga> iterator() {
		return mangas.iterator();
	}
}
