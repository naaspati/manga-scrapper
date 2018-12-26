package sam.ms;

import java.io.IOException;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import sam.downloader.db.DownloaderDB;
import sam.ms.entities.Manga;
import sam.ms.extras.Utils;

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
		if(Utils.dryRun()) {
			mangas = new ArrayList<>();	
		} else {
			DownloaderDB db = new DownloaderDB(Paths.get("download.db"));
			mangas = db.read(new DownloaderFactory());
			mangas.forEach(m -> mangasMap.put(m.getMangaId(), m));
			
			Utils.addShutdownHook(() -> {
				try {
					db.save(mangas);
					System.out.println("saved: download.db");
				} catch (SQLException | IOException e) {
					e.printStackTrace();
				}
			});
		}
	}
	public Manga get(Integer manga_id) {
		return mangasMap.get(Objects.requireNonNull(manga_id));
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
