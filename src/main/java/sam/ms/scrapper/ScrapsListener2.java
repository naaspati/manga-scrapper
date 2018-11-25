package sam.ms.scrapper;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;

import sam.downloader.db.entities.meta.IDChapter;
import sam.downloader.db.entities.meta.IDManga;
import sam.downloader.db.entities.meta.IDPage;

public interface ScrapsListener2 extends ScrapsListener {

	ExecutorService executorService();
	List<IDChapter> getChapters(IDManga manga);
	List<IDPage> getPages(IDManga manga, IDChapter chapter, Path chapterFolder);
	Path getChapterFolder(IDManga manga, Path mangaDir, IDChapter chapter);
	Path getMangaDir(IDManga manga, Path saveRoot);
}
