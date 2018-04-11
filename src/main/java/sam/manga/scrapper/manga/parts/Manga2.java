package sam.manga.scrapper.manga.parts;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import mangafoxscrapper.scrapper.Scrapper2;
import sam.manga.scrapper.units.Manga;
import sam.tsv.Row;

public class Manga2 extends Manga {
    private static final long serialVersionUID = 5373947659267262969L;

    public Manga2(ResultSet rs, String url) throws SQLException {
        super(rs, url);
    }
    public Manga2(int id, String dirName, String mangaName, String url) {
        super(id, dirName, mangaName, url);
    }
    public Manga2(Row row, Scrapper2 scrapper) {
        super(row, scrapper);
    }
    private transient ChapterFilter filter;
    
    public void setFilter(ChapterFilter filter) {
        Objects.requireNonNull(filter);
        Objects.requireNonNull(filter.getTester());
        
        this.filter = filter;
    }
    public ChapterFilter getFilter() {
        return filter;
    }
    
}