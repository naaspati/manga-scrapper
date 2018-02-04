package sam.manga.scrapper.scrappers;

import sam.manga.scrapper.extras.Count;
import sam.manga.scrapper.manga.parts.Chapter;
import sam.manga.scrapper.manga.parts.Manga;

public interface IScrapper {
    public String getImageUrl(String pageUrl) throws Exception;
    public Count extractPages(final Chapter chapter) throws Exception ;
    public void extractChapters(final Manga manga) throws Exception;
}
