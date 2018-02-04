package sam.manga.scrapper.manga.parts;
import static sam.console.ansi.ANSI.red;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Chapter implements Serializable {
    private static final long serialVersionUID = 1134714995970406648L;

    private Page[] pages;
    public final String volume;
    public final double number;
    public final String title;
    public final String url;

    public boolean scrappingCompleted = false;

    public Chapter(String volume, double number, String title, String url) {
        this.volume = volume;
        this.number = number;
        this.title = title;
        this.url = url;
    }

    public boolean isEmpty() {
        return pages == null || pages.length == 0;
    }
    public void forEach(BiConsumer<Integer, Page> consumer) {
        if(isEmpty())
            return;

        IntStream.range(0, pages.length)
        .forEach(i -> consumer.accept(i, pages[i]));
    }

    public int getHashId() {
        return url.hashCode(); 
    }

    public void addPage(int order, Page page) {
        pages[order] = page;
    }
    public void setPageCount(int count) {
        if(count == 0)
            System.out.println(red("page-count = 0"));
        if(pages != null && pages.length != 0 && pages.length < count) {
            System.out.println(red("page-count mismatch, old: ")+pages.length+red(", new: ")+count);
            return;
        }
        pages = pages == null ? new Page[count] : Arrays.copyOf(pages, count);
    }

    public boolean hasPage(int order) {
        return Optional
                .ofNullable(pages)
                .filter(p -> order < p.length)
                .map(p -> p[order])
                .map(p -> p.imageUrl).isPresent();
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