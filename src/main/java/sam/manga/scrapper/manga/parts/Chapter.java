package sam.manga.scrapper.manga.parts;
import static sam.console.ansi.ANSI.red;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import sam.manga.scrapper.extras.Errors;

public class Chapter implements Serializable {
    private static final long serialVersionUID = 1134714995970406648L;

    public final int mangaid;
    private Page[] pages;
    public final String volume;
    public final double number;
    public final String title;
    public final String url;

    public boolean scrappingCompleted = false;

    public Chapter(int mangaid, String volume, double number, String title, String url) {
        this.volume = volume;
        this.number = number;
        this.title = title;
        this.url = url;
        this.mangaid = mangaid;
    }

    public boolean isEmpty() {
        return pages == null || pages.length == 0 || Stream.of(pages).allMatch(Objects::isNull);
    }
    public void forEach(Consumer<Page> consumer) {
        if(isEmpty())
            return;
        for (Page p : pages) consumer.accept(p);
    }

    public int getHashId() {
        return url.hashCode(); 
    }

    public void addPage(int order, String pageUrl) {
        if(pages == null)
            pages = new Page[100];
        if(order >= pages.length)
            pages = Arrays.copyOf(pages, order + 1);
        
        if(pages[order] == null)
            pages[order] = new Page(order, pageUrl, null);
    }
    public void setPageCount(int count) {
        if(count == 0) {
            System.out.println(red("  page-count = 0: ")+url);
            Errors.CHAPTER_PAGES_ZERO.addError(mangaid, number, null, url);
        }
        
        if(pages != null && pages.length != 0 && pages.length < count) {
            System.out.println(red("page-count mismatch, old: ")+pages.length+red(", new: ")+count);
            return;
        }
        pages = pages == null ? new Page[count] : pages.length == count ? pages : Arrays.copyOf(pages, count);
    }
    public void update(){
        scrappingCompleted = isEmpty() ? false : Stream.of(pages).allMatch(p -> p != null && p.imageUrl != null);        
    }
    public int getPageCount() {
        return pages == null ? 0 : pages.length;
    }
    public Stream<Page> pageStream() {
        return pages == null ? Stream.empty() : Stream.of(pages); 
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Chapter [id=").append(getHashId()).append(", volume=")
        .append(volume).append(", number=").append(number).append(", title=").append(title).append(", url=")
        .append(url).append(", pageCount=").append(getPageCount()).append(", scrappingCompleted=")
        .append(scrappingCompleted).append("]");
        return builder.toString();
    }
}