package sam.ms.entities;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import sam.downloader.db.entities.impl.DChapterImpl;
import sam.downloader.db.entities.meta.DStatus;
import sam.downloader.db.entities.meta.IDPage;
import sam.manga.samrock.Renamer;
import sam.manga.scrapper.FailedChapter;
import sam.manga.scrapper.ScrappedChapter;
import sam.manga.scrapper.ScrappedPage;
import sam.manga.scrapper.ScrapperException;

public class Chapter extends DChapterImpl {
	private final Path path;
	public ScrappedChapter sc;

	{
		this.path = getManga().getPath().resolve(Renamer.makeChapterFileName(getNumber(), getTitle(), manga.getMangaName()));
	}
	public Chapter(ScrappedChapter sc, Manga manga) {
		super(manga, sc instanceof FailedChapter ? -1 : sc.getNumber(), sc.getTitle(), sc.getUrl());
		this.volume = sc.getVolume();
		this.sc = sc;
	}
	public Chapter(Manga manga, double number, String title, String volume, String source, String target, String url, String error, DStatus status) {
		super(manga, number, title, volume, source, target, url, error, status);
	}
	
	public void setScrappedChapter(ScrappedChapter sc) {
		this.sc = sc;
	}
	public int getHashId() { return getUrl().hashCode(); }

	@Override public Path getSource() {
		if(source == null) return null;
		if(source instanceof Path) return (Path)source;
		Path p = Paths.get(source.toString());
		source = p;
		return p;
	}
	@Override public Path getTarget() { 
		if(target == null) return null;
		if(target instanceof Path) return (Path)target;
		Path p = Paths.get(target.toString());
		target = p;
		return p;
	}
	public void setSourceTarget(Path chapterFolder) {
		this.source = chapterFolder;
		this.target = chapterFolder;
	}
	@Override
	public Manga getManga() {
		return (Manga)super.getManga();
	}
	public Path getPath() {
		return path;
	}
	@Override
	public List<IDPage> getPages() {
		return pages;
	}

	public void scrapPages() throws ScrapperException, IOException {
		for (ScrappedPage sp : sc.getPages()) {
			IDPage p = findPage(sp.getPageUrl());
			if(p == null)
				addPage(p = new Page(this, sp.getOrder(), sp.getPageUrl()));

			if(sp.getImgUrl() != null)
				((Page)p).setImgUrl(sp.getImgUrl());
		}
	}
	public String[] getImageUrls(String pageUrl) throws ScrapperException, IOException {
		return		sc.getPageImageUrl(pageUrl);
	}
}