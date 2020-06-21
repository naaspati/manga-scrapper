package sam.ms.entities;
import java.util.Objects;

import sam.api.store.entities.meta.SPage;

public class Page extends StatusContainer implements SPage {
	protected final int id;
	protected int order;
    protected final String page_url;
    protected String img_url;
    
    protected transient Chapter chapter;
    
    public Page(Chapter chapter, int id, int order, String page_url){
    	this(chapter, id, order, page_url, null);
    }
    public Page(Chapter chapter, int id, int order, String page_url, String img_url){
    	this.id = id;
    	this.chapter = Objects.requireNonNull(chapter);
        this.order = order;
        this.page_url = Objects.requireNonNull(page_url);
        this.img_url = img_url;
    }

    @Override
    public int getId() { return id; }
    @Override public int getOrder(){ return this.order; }
    @Override public String getPageUrl(){ return this.page_url; }
    @Override public String getImgUrl(){ return this.img_url; }
	@Override
	public void setImgUrl(String url) {
		this.img_url = url;
	}
	public Chapter getChapter() {
		return chapter;
	}
	public void init(Chapter chapter) {
		if(this.chapter != null)
			throw new IllegalStateException("already initialized");
		
		this.chapter = Objects.requireNonNull(chapter);
	}
}