package sam.manga.scrapper.scrappers;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import sam.manga.newsamrock.urls.MangaUrlsMeta;
import sam.manga.scrapper.manga.parts.Chapter;
import sam.manga.scrapper.manga.parts.Manga;

public class MangaFox extends AbstractScrapper {
    public String getImageUrl(Document doc) throws Exception {
        Element urlE = doc.getElementById("image");

        String imageUrl = urlE != null ? urlE.absUrl("src") : null;

        if(imageUrl == null){
            Elements els = doc.select(".read_img a img");
            imageUrl = els.isEmpty() ? null : els.get(0).absUrl("src");
        }

        Objects.requireNonNull(imageUrl);
        return imageUrl;        
    }
    @Override
    public int _extractPages(final Chapter chapter) throws Exception { 
        int total[] = {0};

        String chapterUrl = chapter.url.trim();
        if(chapterUrl.endsWith("/"))
            chapterUrl.substring(0, chapterUrl.length() - 1);

        String base = chapterUrl.substring(0, chapterUrl.lastIndexOf('/'));

        Document doc = getDocument(base+"/1.html");
        doc.select("select.m option").stream()
        .map(Element::text)
        .distinct()
        .filter(s -> !"comment".equalsIgnoreCase(s) && !"comments".equalsIgnoreCase(s))
        .forEach(orderString -> {
            String pageUrl = base+"/"+orderString+".html";
            int order = Integer.parseInt(orderString) - 1;
            chapter.addPage(order, pageUrl);
            total[0]++;
        });

        return total[0];
    }

    public void extractChapters(final Manga manga) throws Exception{
        Document doc = getDocument(manga.url);

        List<Chapter> list = 
                doc.select("#chapters .chlist li div").stream()
                .flatMap(e -> e.children().stream())
                .filter(e -> e.tagName().matches("h\\d"))
                .map(elm -> {
                    Element elm2 = elm.getElementsByClass("tips").get(0);
                    String url = elm2.absUrl("href");
                    url = url.endsWith("html") ? url : url + (url.endsWith("/") ? "1.html" : "/1.html");
                    String[] splits = url.split("/");
                    Elements titles = elm.select(".title.nowrap");
                    String title = titles.isEmpty() ? "" : titles.get(0).text();
                    String numberS = splits[splits.length - 2];
                    String volume = splits[splits.length - 3];
                    volume = volume.matches("v\\d+") ? volume : "vUnknown"; 
                    numberS = numberS.charAt(0) == 'c' ? numberS.substring(1) : numberS;
                    double number = Double.parseDouble(numberS);

                    return manga.getChapter(number, new Chapter(manga.id, volume, number, title, url));
                }).collect(Collectors.toList()  );

        Collections.reverse(list);
        manga.addChapters(list);
    }
    @Override
    public String getUrlColumnName() {
        return MangaUrlsMeta.MANGAFOX;
    }
}
