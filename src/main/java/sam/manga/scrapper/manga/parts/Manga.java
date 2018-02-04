package sam.manga.scrapper.manga.parts;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import sam.tsv.Row;

public class Manga implements Serializable {

    private static final long serialVersionUID = -4611704559346175933L;

    public final int id;
    public final String name;
    public final String url;
    public final Map<Double, Chapter> chaptersMap = new LinkedHashMap<>();

    public Manga(int id, String name, String url) {
        this.id = id;
        this.name = name;
        this.url = url;
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
    public Chapter getChapter(double number, Supplier<? extends Chapter> orElse) {
        return Optional.ofNullable(chaptersMap.get(number))
                .orElseGet(orElse);
    }
    public Manga(int id, Row row) {
        this.id = id; 
        this.name = row.get("manga_name");
        this.url = row.get("url");
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