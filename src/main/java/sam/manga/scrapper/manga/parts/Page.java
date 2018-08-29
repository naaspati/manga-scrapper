package sam.manga.scrapper.manga.parts;
import java.nio.file.Path;

import sam.manga.scrapper.ScrapperPage;
import sam.manga.scrapper.jsoup.JsoupFactory;

public class Page {
    private transient ScrapperPage scrapperPage;
    
    public void setScrapperPage(ScrapperPage scrapperPage) {
		this.scrapperPage = scrapperPage;
	}
    public Page(ScrapperPage page) {
        this.scrapperPage = page;
    }
	public int getOrder() {
		return scrapperPage.getOrder();
	}
	public String getPageUrl() {
		return scrapperPage.getPageUrl();
	}
	public String getImageUrl() {
		return scrapperPage.getImageUrl();
	}
	public void setImageUrl(String imageUrl) {
		scrapperPage.setImageUrl(imageUrl);
	}
	public String extractImageUrl(JsoupFactory factory) throws Exception {
		return scrapperPage.extractImageUrl(factory);
	}
	public Path getPath(Path chapterFolder) {
		return chapterFolder.resolve(String.valueOf(getOrder()));
	}
}