package sam.ms.scrapper;

import java.util.List;
import java.util.concurrent.ExecutorService;

import sam.downloader.db.entities.meta.IDChapter;
import sam.downloader.db.entities.meta.IDManga;
import sam.downloader.db.entities.meta.IDPage;
import sam.manga.scrapper.Scrapper;

interface ScrapsListener2 extends ScrapsListener {
	ExecutorService executorService();
	List<IDChapter> getChapters(Scrapper scrapper, IDManga manga);
	List<IDPage> getPages(Scrapper scrapper, IDManga manga, IDChapter chapter);
}
