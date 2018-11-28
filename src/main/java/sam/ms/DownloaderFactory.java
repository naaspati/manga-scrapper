package sam.ms;
import sam.downloader.db.DownloaderDBFactory;
import sam.downloader.db.entities.meta.DStatus;
import sam.downloader.db.entities.meta.IDChapter;
import sam.downloader.db.entities.meta.IDManga;
import sam.ms.entities.Chapter;
import sam.ms.entities.Manga;
import sam.ms.entities.Page;
import sam.ms.extras.Utils;

public class DownloaderFactory implements DownloaderDBFactory {
	
	@Override
	public Manga createManga(int manga_id, String dir_name, String manga_name, String url, String error, String status) {
		return new Manga(Utils.MANGA_DIR, manga_id, dir_name, manga_name, url, error, DStatus.status(status));
	}
	@Override
	public Chapter createChapter(IDManga manga, double number, String title, String volume, String source, String target, String url, String error, String status) {
		return new Chapter((Manga)manga, number, title,volume, source, target, url, error, DStatus.status(status));
	}
	@Override
	public Page createPage(IDChapter chapter, int order, String page_url, String img_url, String error, String status){
		return new Page(chapter, order, page_url, img_url, error, DStatus.status(status));
	}
}
