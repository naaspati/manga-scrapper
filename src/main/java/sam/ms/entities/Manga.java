package sam.ms.entities;
import static sam.manga.samrock.mangas.MangasMeta.DIR_NAME;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_ID;
import static sam.manga.samrock.mangas.MangasMeta.MANGA_NAME;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;

import sam.api.store.entities.meta.MutableList;
import sam.api.store.entities.meta.SChapter;
import sam.api.store.entities.meta.SManga;
import sam.collection.Iterators;
import sam.manga.samrock.chapters.MinimalChapter;

public class Manga extends StatusContainer implements SManga {
	public static String[] dbFields() {
		return new String[]{MANGA_ID, DIR_NAME, MANGA_NAME};
	}
	
	protected final int manga_id;
	protected final String dir_name;
	protected final String manga_name;
	protected String[] urls;
	protected final List<Chapter> chapters = new ArrayList<>();
	
	public Manga(SQLiteStatement rs) throws SQLiteException {
		this.manga_id = rs.columnInt(0);
		this.dir_name = rs.columnString(1);
		this.manga_name = rs.columnString(2);
	}

	public Manga(int manga_id, String dir_name, String manga_name, String[] urls) {
		this.manga_id = manga_id;
		this.dir_name = dir_name;
		this.manga_name = manga_name;
		this.urls = urls;
	}
	
	public void setUrls(String[] urls) {
		this.urls = urls;
	}

	@Override public int getId(){ return this.manga_id; }
	@Override public int getMangaId(){ return this.manga_id; }
	@Override public String getDirName(){ return this.dir_name; }
	@Override public String getMangaName(){ return this.manga_name; }
	@Override public String[] getUrls(){ return this.urls; }
	public MutableList<? extends SChapter> getChapters() {
		return new MutableList<Chapter>(chapters) {
			@Override
			protected Object keyOf(Chapter item) {
				return item == null ? null : item.getUrl();
			}
		};
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Iterator<MinimalChapter> iterator() {
		Iterator itr = chapters == null ? Iterators.empty() : chapters.iterator();
		return itr;
	}
	public void setChapters(List<Chapter> chapters) {
		this.chapters.clear();
		this.chapters.addAll(chapters);
	}
    
	@Override
	public int size() {
		return this.chapters.size();
	}

	@Deprecated
	@Override
	public Path getDirPath() {
		throw new IllegalAccessError();
	}

	public void init() {
		this.chapters.forEach(c -> c.init(this));
	}
}