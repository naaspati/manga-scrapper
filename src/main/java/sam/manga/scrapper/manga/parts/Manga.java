package sam.manga.scrapper.manga.parts;
import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static sam.manga.newsamrock.mangas.MangasMeta.*;
import sam.manga.scrapper.scrappers.Scrapper;
import sam.tsv.Row;

public class Manga implements Serializable {

    private static final long serialVersionUID = -4611704559346175933L;

    public final int id;
    public final String dirName;
    public final String mangaName;
    public final String url;
    public final Map<Double, Chapter> chaptersMap = new LinkedHashMap<>();
    private transient ChapterFilter filter;

    public Manga(int id, String dirName, String mangaName, String url) {
        this.id = id;
        this.dirName = dirName;
        this.mangaName = mangaName;
        this.url = url;
    }
    public Manga(ResultSet rs, String url) throws SQLException {
        this.id = getMangaId(rs);
        this.mangaName = getMangaName(rs);
        this.dirName = getDirName(rs);
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
    public Manga(Row row) {
        this.id = row.getInt(MANGA_ID); 
        this.dirName = row.get(DIR_NAME);
        this.mangaName = row.get(MANGA_NAME);
        this.url = row.get(Scrapper.getInstance().getUrlColumnName());
    }

    public void forEach(BiConsumer<Double, Chapter> consumer) {
        chaptersMap.forEach(consumer);
    }
    @Override
    public String toString() {
        return "Manga [id=" + id + ", dirName=" + dirName + ", mangaName=" + mangaName + ", url=" + url
                + ", chaptersMap=" + chaptersMap + "]";
    }
    
}