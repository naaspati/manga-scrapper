package sam.ms.entities;
import java.nio.file.Path;
import java.nio.file.Paths;

import sam.downloader.db.entities.impl.DChapterImpl;
import sam.downloader.db.entities.meta.DStatus;
import sam.downloader.db.entities.meta.IDManga;
import sam.manga.samrock.chapters.ChapterUtils;

public class Chapter extends DChapterImpl {
	
	public Chapter(IDManga manga, double number, String title, String url, String volume) {
		super(manga, number, title, url);
		this.volume =volume;
	}
	public Chapter(IDManga manga, double number, String title, String volume, Object source, Object target, String url, String error, DStatus status) {
		super(manga, number, title, volume, source, target, url, error, status);
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
	public Path dirPath(Path mangaDir, Manga manga) {
		return mangaDir.resolve(ChapterUtils.makeChapterFileName(getNumber(), getTitle(), manga.getMangaName()));
	}
	public void setSourceTarget(Path chapterFolder) {
		this.source = chapterFolder;
		this.target = chapterFolder;
	}
	@Override
	public Manga getManga() {
		return (Manga)super.getManga();
	}
}