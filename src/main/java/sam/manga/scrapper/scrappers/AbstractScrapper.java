package sam.manga.scrapper.scrappers;

import static sam.console.ansi.ANSI.cyan;
import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import sam.manga.scrapper.extras.Count;
import sam.manga.scrapper.manga.parts.Chapter;
import sam.manga.scrapper.manga.parts.Manga;
public abstract class AbstractScrapper {
    protected Document getDocument(String url) throws MalformedURLException, IOException {
        return Jsoup.parse(new URL(url), 15000);
    }
    public String getImageUrl(String pageUrl) throws Exception {
        return getImageUrl(getDocument(pageUrl));
    }
    
    private static final String pagesCountFormat = cyan("(")+yellow("%d")+cyan("): ");
    public Count extractPages(Chapter chapter) throws Exception {
        Count count = new Count();
        count.total = _extractPages(chapter);
        chapter.setPageCount(count.total);
        
        System.out.printf(pagesCountFormat, count.total);
        
        chapter.forEach(page -> {
            if(page == null)
                return;
            String orderS = page.order +" ";
            if(page.imageUrl != null) {
                System.out.print(orderS);
                count.success++;
                return;
            }
            
            try {
                page.imageUrl = getImageUrl(page.pageUrl);
            } catch (Exception e) {}
            
            if(page.imageUrl != null)
                count.success++;

            System.out.print(page.imageUrl == null ? red(orderS) : orderS);
        });
        System.out.println();
        return count;
    };
    
    /**
     * extracts pages, adds them to chapter, returns total chapter found
     * @param chapter
     * @return
     * @throws Exception
     */
    protected abstract int _extractPages(Chapter chapter) throws Exception;
    public abstract String getImageUrl(Document doc) throws Exception;
    public abstract void extractChapters(final Manga manga) throws Exception;
    public abstract String getUrlColumnName();
}
