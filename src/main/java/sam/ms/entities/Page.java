package sam.ms.entities;
import java.nio.file.Path;

import sam.downloader.db.entities.impl.DPageImpl;
import sam.downloader.db.entities.meta.DStatus;
import sam.downloader.db.entities.meta.IDChapter;

public class Page extends DPageImpl {
	private Path target;
	
    public Page(IDChapter chapter, int order, String page_url, String img_url, String error, DStatus status) {
		super(chapter, order, page_url, img_url, error, status);
	}
	
	public Page(IDChapter chapter, int order, String page_url) {
		super(chapter, order, page_url);
	}
	public void setImgUrl(String url) {
		this.img_url = url;
	}
	@Override
	public Chapter getChapter() {
		return (Chapter) super.getChapter();
	}
	public void setTarget(Path target) {
		this.target = target;
	}
	public Path getTarget() {
		return target;
	}
}