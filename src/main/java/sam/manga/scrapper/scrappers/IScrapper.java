package sam.manga.scrapper.scrappers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import sam.manga.scrapper.extras.Count;
import sam.manga.scrapper.manga.parts.Chapter;
import sam.manga.scrapper.manga.parts.Manga;

public interface IScrapper {
    default Document getDocument(String url) throws MalformedURLException, IOException {
        return Jsoup.parse(new URL(url), 15000);
    }
    public String getImageUrl(String pageUrl) throws Exception;
    public Count extractPages(final Chapter chapter) throws Exception ;
    public void extractChapters(final Manga manga) throws Exception;
}
