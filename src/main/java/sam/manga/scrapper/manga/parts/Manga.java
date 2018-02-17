package sam.manga.scrapper.manga.parts;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import sam.manga.newsamrock.mangas.MangasMeta;
import sam.manga.scrapper.scrappers.Scrapper;
import sam.tsv.Row;

public class Manga implements Serializable {

    private static final long serialVersionUID = -4611704559346175933L;

    public final int id;
    public final String name;
    public final String url;
    public final Map<Double, Chapter> chaptersMap = new LinkedHashMap<>();
    private transient ChapterFilter filter;

    public Manga(int id, String name, String url) {
        this.id = id;
        this.name = name;
        this.url = url;
    }
    public void setFilter(ChapterFilter filter) {
        Objects.requireNonNull(filter);
        Objects.requireNonNull(filter.getTester());
        
        this.filter = filter;
    }
    public ChapterFilter getFilter() {
        return filter;
    }
    public void removeChapterIf(Predicate<? super Chapter> filter) {
        chaptersMap.values().removeIf(filter);
    }
    public int chaptersCount() {
        return chaptersMap.size();
    }
    public void addChapters(List<Chapter> list) {
        list.forEach(c -> chaptersMap.put(c.number, c));
    }
    public boolean isEmpty() {
        return chaptersMap.isEmpty();
    }
    public Stream<Chapter> chapterStream() {
        return chaptersMap.values().stream();
    }
    public Chapter getChapter(double number, Chapter orElse) {
        Chapter c = chaptersMap.get(number);
        return c == null ? orElse : c;
    }
    public Manga(int id, Row row) {
        this.id = id; 
        this.name = row.get(MangasMeta.MANGA_NAME);
        this.url = row.get(Scrapper.getInstance().getUrlColumnName());
    }

    public void forEach(BiConsumer<Double, Chapter> consumer) {
        chaptersMap.forEach(consumer);
    }
    @Override
    public String toString() {
        return new StringBuilder().append("Manga [id=").append(id).append(", name=").append(name).append(", url=").append(url)
                .append("]").toString();
    }
}