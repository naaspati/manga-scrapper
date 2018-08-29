package sam.manga.scrapper.manga.parts;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import sam.collection.Iterators;
import sam.manga.newsamrock.chapters.ChapterUtils;
import sam.manga.scrapper.ScrapperChapter;

public class Chapter implements Iterable<Page> {
	public final  int mangaid;
	private transient ScrapperChapter scrapperChapter;
	private List<Page> pages;

	public Chapter(int mangaid, ScrapperChapter scrapperChapter) {
		this.mangaid = mangaid;
		this.scrapperChapter = scrapperChapter;
	}
	public String getVolume() {
		return scrapperChapter.getVolume();
	}
	public double getNumber() {
		return scrapperChapter.getNumber();
	}
	public String getTitle() {
		return scrapperChapter.getTitle();
	}
	public String getUrl() {
		return scrapperChapter.getUrl();
	}
	public int getHashId() {
		return getUrl().hashCode();
	}
	public int nonNullPageCount() {
		throw new IllegalAccessError("NOT YET IMPLEMENTED");
	}
	@Override
	public Iterator<Page> iterator() {
		return pages == null ? Iterators.empty() : pages.iterator();
	}
	public Stream<Page> stream() {
    	return pages == null ? Stream.empty() : pages.stream();
    }
	public List<Page> loadPages() throws Exception {
		if(pages != null) return pages;
		pages = scrapperChapter.getPages().map(p -> new Page(p)).collect(Collectors.toList());
		return pages;
	}
	public Path dirPath(Path mangaDir, Manga manga) {
		return mangaDir.resolve(ChapterUtils.makeChapterFileName(getNumber(), getTitle(), manga.mangaName));
	}
}