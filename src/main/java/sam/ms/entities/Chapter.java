package sam.ms.entities;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import sam.api.store.entities.meta.MutableList;
import sam.api.store.entities.meta.SChapter;
import sam.api.store.entities.meta.SManga;
import sam.api.store.entities.meta.SPage;
import sam.collection.Iterators;
import sam.manga.api.scrapper.ScrappedChapter;

public class Chapter extends StatusContainer implements SChapter {
	protected final int id;
	protected final double number;
	protected final String title;
	protected final String url;
	
	protected Path source;
	protected Path target;
	protected String volume;
	
	protected final List<Page> pages;
	protected transient Manga manga;
	
	public Chapter(Manga manga, int id, double number, String title, Path source, Path target, String url, String volume, List<Page> pages) {
		this.manga = Objects.requireNonNull(manga);
		this.id = id;
		this.number = number;
		this.title = title;
		this.source = source;
		this.target = target;
		this.url = url;
		this.volume = volume;
		this.pages = pages == null ? new ArrayList<>() : pages;
	}
	
	public Chapter(Manga manga, ScrappedChapter sc, int id) {
		this.manga = Objects.requireNonNull(manga);
		this.id = id;
		this.number = sc.getNumber();
		this.title = sc.getTitle();
		this.url = sc.getUrl();
		this.pages = new ArrayList<>();
	}

	@Override
    public int getId() { return id; }

	public Path getSource() {
		return source;
	}
	public Path getTarget() { 
		return target;
	}
	public void setSourceTarget(Path chapterFolder) {
		this.source = chapterFolder;
		this.target = chapterFolder;
	}
	
	public SManga getManga() { return manga; }
	@Override public String getVolume() { return volume; }
	@Override public double getNumber(){ return this.number; }
	@Override public String getTitle(){ return this.title; }
	@Override public String getUrl(){ return this.url; }
	@Override public String getError(){ return this.error; }
	public MutableList<Page> getPages() {
		return new MutableList<Page>(pages) {
			@Override  
			protected Object keyOf(Page item) {
				return item == null ? null : item.getPageUrl();
			}
		};
	}
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Iterator<SPage> iterator() {
		Iterator itr = pages == null ? Iterators.empty() : pages.iterator(); 
		return itr;
	}
	
	@Override
	public int size() {
		return pages == null ? 0 : pages.size();
	}
	private String filename;

	@Override
	public String getFileName() {
		return filename;	
	}

	public void init(Manga manga) {
		if(this.manga != null)
			throw new IllegalStateException("already initialized");
		
		this.manga = Objects.requireNonNull(manga);
		this.pages.forEach(p -> p.init(this));
	}
}