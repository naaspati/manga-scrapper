package sam.manga.scrapper.scrappers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import sam.manga.newsamrock.urls.MangaUrlsMeta;
import sam.manga.scrapper.extras.Errors;
import sam.manga.scrapper.manga.parts.Chapter;
import sam.manga.scrapper.manga.parts.Manga;

public class MangaHere extends AbstractScrapper {
    @Override
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
    protected int _extractPages(Chapter chapter) throws Exception {
        int total[] = {0};

        Document doc = getDocument(chapter.url); 

        doc.getElementsByTag("select").stream()
        .filter(e -> e.attr("onchange") != null && e.attr("onchange").startsWith("change_page"))
        .findFirst()
        .map(e -> e.getElementsByTag("option").stream())
        .orElse(Stream.empty())
        .map(e -> e.absUrl("value"))
        .filter(s -> !s.endsWith("featured.html"))
        .forEach(pageUrl -> {
            int order;
            if(pageUrl.endsWith("/"))
                order = 1;
            else
                order = Integer.parseInt(pageUrl.substring(pageUrl.lastIndexOf('/') + 1, pageUrl.length() - 5));

            total[0]++;
            chapter.addPage(order - 1, pageUrl);
        });
        return total[0];
    }

    @Override
    public void extractChapters(Manga manga) throws Exception {
        String mangaurl = manga.url;

        List<Chapter> chaps = new ArrayList<>();

        getDocument(mangaurl)
        .getElementsByTag("a")
        .forEach(elm -> {
            String url = elm.absUrl("href");

            if(!elm.absUrl("href").startsWith(mangaurl))
                return;

            String title = elm.text();

            if("comment".equalsIgnoreCase(title) || "comments".equalsIgnoreCase(title))
                return;

            if(elm.parent().text().trim().startsWith("Status:Ongoing"))
                return;

            String[] splits = url.split("/");
            String numberS = splits[splits.length - 1];
            String volume = splits[splits.length - 2];
            volume = volume.matches("v\\d+") ? volume : "vUnknown"; 
            numberS = numberS.charAt(0) == 'c' ? numberS.substring(1) : numberS;
            double number = 0;
            try {
                number = Double.parseDouble(numberS);
            } catch (NumberFormatException e) {
                Errors.CHAPTER.addError(manga.id, null, e, "Bad chapter number: "+numberS, url);
                return;
            }
            chaps.add(manga.getChapter(number, new Chapter(manga.id, volume, number, title, url)));
        });

        Collections.reverse(chaps);
        manga.addChapters(chaps);
    }

    @Override
    public String getUrlColumnName() {
        return MangaUrlsMeta.MANGAHERE;
    }

}
