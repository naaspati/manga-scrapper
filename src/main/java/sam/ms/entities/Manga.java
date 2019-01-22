package sam.ms.entities;
import static sam.manga.samrock.mangas.MangasMeta.DIR_NAME;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_ID;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_NAME;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.DoublePredicate;

import sam.downloader.db.entities.impl.DMangaImpl;
import sam.downloader.db.entities.meta.DStatus;
import sam.downloader.db.entities.meta.IDChapter;
import sam.manga.samrock.Renamer;
import sam.manga.scrapper.FailedChapter;
import sam.manga.scrapper.ScrappedChapter;
import sam.manga.scrapper.ScrappedManga;
import sam.manga.scrapper.ScrapperException;
import sam.tsv.Row;

public class Manga extends DMangaImpl {

	private String _url;
	private transient DoublePredicate filter;
	private int limit = Integer.MAX_VALUE;
	private final Path path;
	
	public Manga(Path mangaRoot, ResultSet rs, String url) throws SQLException {
		super(rs.getInt(MANGA_ID), rs.getString(DIR_NAME), rs.getString(MANGA_NAME), url, null, null);
		this._url = url;
		this.path = mangaRoot.resolve(dir_name);
    }
	public Manga(Path mangaRoot, Row row, String urlColumn) {
		super(row.getInt(MANGA_ID), Renamer.mangaDirName(row.get(MANGA_NAME)), row.get(MANGA_NAME), row.get(urlColumn), null, null);
		this._url = url;
		this.path = mangaRoot.resolve(dir_name);
    }
	
	public Manga(Path mangaRoot, int manga_id, String dir_name, String manga_name, String url, String error, DStatus status) {
		super(manga_id, dir_name, manga_name, url, error, status);
		this._url = url;
		this.path = mangaRoot.resolve(dir_name);
	}
	@Override
	public Chapter addChapter(IDChapter chapter) {
		return (Chapter) super.addChapter(chapter);
	}
    public void setFilter(DoublePredicate filter) {
        this.filter = Objects.requireNonNull(filter);
    }
    public DoublePredicate getFilter() {
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
	public Path getPath() {
		return path;
	}
	
	public int scrapChapters(ScrappedManga sm, BiConsumer<FailedChapter, IDChapter> onFailed) throws IOException, ScrapperException {
		ScrappedChapter[] chaps = sm.getChapters();

		int count = 0;
		Chapter c;
		for (ScrappedChapter sc : chaps) {
			if(sc instanceof FailedChapter) {
				FailedChapter f = (FailedChapter) sc;
				c = addChapter(new Chapter(sc, this));
				onFailed.accept(f, c);
				c.setFailed(f.toString(), f.getException());
			} else {
				c = (Chapter)findChapter(sc.getUrl());
				if(c == null) {
					c = new Chapter(sc, this);
					addChapter(c);
				}
				count++;
			}
			c.setScrappedChapter(sc);
		}
		return count;
	}
}