package sam.ms.scrapper;

import java.util.List;
import java.util.concurrent.ExecutorService;

import sam.downloader.db.entities.meta.IDChapter;
import sam.downloader.db.entities.meta.IDManga;
import sam.downloader.db.entities.meta.IDPage;

interface ScrapsListener2 extends ScrapsListener {
	ExecutorService executorService();
	List<IDChapter> getChapters(IDManga manga);
	List<IDPage> getPages(IDManga manga, IDChapter chapter);
}
