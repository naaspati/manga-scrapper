package sam.ms.entities;
import static sam.manga.samrock.mangas.MangasMeta.DIR_NAME;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_ID;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_NAME;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import sam.downloader.db.entities.impl.DMangaImpl;
import sam.downloader.db.entities.meta.DStatus;
import sam.downloader.db.entities.meta.IDChapter;
import sam.manga.samrock.mangas.MangaUtils;
import sam.tsv.Row;

public class Manga extends DMangaImpl {

	private String _url;
	private transient ChapterFilter filter;
	private int limit = Integer.MAX_VALUE;
	
	public Manga(ResultSet rs, String url) throws SQLException {
		super(rs.getInt(MANGA_ID), rs.getString(DIR_NAME), rs.getString(MANGA_NAME), url, null, null);
		this._url = url;
    }
	public Manga(Row row, String urlColumn) {
		super(row.getInt(MANGA_ID), MangaUtils.toDirName(row.get(MANGA_NAME)), row.get(MANGA_NAME), row.get(urlColumn), null, null);
		this._url = url;
    }
	
	public Manga(int manga_id, String dir_name, String manga_name, String url, String error, DStatus status) {
		super(manga_id, dir_name, manga_name, url, error, status);
		this._url = url;
	}
	@Override
	public Chapter addChapter(IDChapter chapter) {
		return (Chapter) super.addChapter(chapter);
	}

    public void setFilter(ChapterFilter filter) {
        Objects.requireNonNull(filter);
        Objects.requireNonNull(filter.getTester());

        this.filter = filter;
    }
    public ChapterFilter getFilter() {
        return filter;
    }
	public Path dirPath(Path mangaDir) {
		return mangaDir.resolve(getDirName());
	}
	@Override
	public String getUrl() {
		return this._url;
	}
	public void setUrl(String url) {
		this._url = url;
	}
	public void setLimit(int limit) {
		this.limit = limit;
	}
	public int getLimit() {
		return limit;
	}
}