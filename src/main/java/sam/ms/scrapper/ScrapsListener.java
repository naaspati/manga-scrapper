package sam.ms.scrapper;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import sam.config.MyConfig;
import sam.downloader.db.entities.meta.DStatus;
import sam.downloader.db.entities.meta.IDChapter;
import sam.downloader.db.entities.meta.IDManga;
import sam.manga.samrock.chapters.MinimalChapter;

public interface ScrapsListener {
	IDManga STOP_MANGA = new IDManga() {
		@Override public Iterator<IDChapter> iterator() { throw new IllegalAccessError(); }
		@Override public int size() { throw new IllegalAccessError(); }
		@Override public String getUrl() { throw new IllegalAccessError(); }
		@Override public DStatus getStatus() { throw new IllegalAccessError(); }
		@Override public String getMangaName() { throw new IllegalAccessError(); }
		@Override public int getMangaId() { throw new IllegalAccessError(); }
		@Override public String getError() { throw new IllegalAccessError(); }
		@Override public String getDirName() { throw new IllegalAccessError(); }
		@Override public IDChapter addChapter(IDChapter page) { throw new IllegalAccessError(); }
		@Override public Path getDirPath() { throw new IllegalAccessError(); }
		@Override public Iterable<? extends MinimalChapter> getChapterIterable()  { throw new IllegalAccessError(); }
	};

	/**
	 * return {@link Scraps#STOP_MANGA} if wish to stop the process 
	 * @return
	 */
	IDManga nextManga();

	Path MANGA_DIR = Paths.get(MyConfig.MANGA_DIR);

	default Path getSaveRootPath() {
		return MANGA_DIR;
	}
	int totalCountOfManga();
	int remainingCountOfManga();

}
