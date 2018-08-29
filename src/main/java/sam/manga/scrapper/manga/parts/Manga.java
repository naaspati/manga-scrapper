package sam.manga.scrapper.manga.parts;
import static sam.manga.newsamrock.mangas.MangasMeta.MANGA_ID;
import static sam.manga.newsamrock.mangas.MangasMeta.MANGA_NAME;
import static sam.manga.newsamrock.mangas.MangasMeta.getDirName;
import static sam.manga.newsamrock.mangas.MangasMeta.getMangaId;

import java.io.Serializable;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import sam.collection.Iterators;
import sam.manga.newsamrock.mangas.MangaUtils;
import sam.manga.newsamrock.mangas.MangasMeta;
import sam.tsv.Row;

public class Manga implements Iterable<Chapter>, Serializable {
	private static final long serialVersionUID = 8895087245686454142L;
	
	public final String dirName;
    public final int id;
    public final String mangaName;
    public final String url;
    
    private transient ChapterFilter filter;
    private transient List<Chapter> chapters;

    public Manga(int id, String mangaName, String url, String dirName) {
    	this.id = id;
    	this.mangaName = mangaName;
    	this.url = url;
        this.dirName = dirName;
    }

    public Manga(ResultSet rs, String url) throws SQLException {
        this(getMangaId(rs), MangasMeta.getMangaName(rs), url, getDirName(rs));
    }
    public Manga(Row row, String urlColumn) {
        this(row.getInt(MANGA_ID), row.get(MANGA_NAME), row.get(urlColumn), MangaUtils.toDirName(row.get(MANGA_NAME)));
    }
    public void setFilter(ChapterFilter filter) {
        Objects.requireNonNull(filter);
        Objects.requireNonNull(filter.getTester());

        this.filter = filter;
    }
    public ChapterFilter getFilter() {
        return filter;
    }
    @Override
    public Iterator<Chapter> iterator() {
    	return chapters == null ? Iterators.empty() : chapters.iterator();
    }
    public Stream<Chapter> stream() {
    	return chapters == null ? Stream.empty() : chapters.stream();
    }
	public void setChapters(List<Chapter> chapters) {
		this.chapters = chapters;
	}

	public Path dirPath(Path mangaDir) {
		return mangaDir.resolve(dirName);
	}
	
}