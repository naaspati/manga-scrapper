package sam.manga.scrapper.scrappers;

import static sam.console.ansi.ANSI.cyan;
import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;

import java.net.URL;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import sam.manga.scrapper.extras.Count;
import sam.manga.scrapper.manga.parts.Chapter;
import sam.manga.scrapper.manga.parts.Manga;
import sam.manga.scrapper.manga.parts.Page;

public class MangaFox implements IScrapper {

    public String getImageUrl(String pageUrl) throws Exception {
        return getImageUrl(Jsoup.parse(new URL(pageUrl), 15000));
    }
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

    final String pagesCountFormat = cyan("(")+yellow("%d")+cyan("): ");
    public Count extractPages(final Chapter chapter) throws Exception { 
        final Count count = new Count();

        String chapterUrl = chapter.url.trim();
        if(chapterUrl.endsWith("/"))
            chapterUrl.substring(0, chapterUrl.length() - 1);

        String base = chapterUrl.substring(0, chapterUrl.lastIndexOf('/'));

        Document doc = Jsoup.parse(new URL(base+"/1.html"), 15000);
        List<String> pages = doc.select("select.m option").stream().map(Element::text).distinct().collect(Collectors.toList());
        pages.remove("Comments");

        count.total = pages.size();
        chapter.setPageCount(pages.size());
        Collections.sort(pages, Comparator.comparing((String s) -> Integer.parseInt(s)));

        System.out.printf(pagesCountFormat, count.total);
        count.success = 0;

        for (String orderString : pages)  {
            String pageUrl = base+'/'+orderString+".html";
            final int order = Integer.parseInt(orderString) - 1;

            if(chapter.hasPage(order)){
                System.out.print(orderString.concat(" "));
                count.success++;
                continue;
            }

            String imageUrl = orderString.equals("1") ? getImageUrl(doc) : getImageUrl(pageUrl);

            chapter.addPage(order, new Page(order, pageUrl, imageUrl));

            if(imageUrl != null)
                count.success++;

            System.out.print(imageUrl == null ? red(orderString.concat(" ")) : orderString.concat(" "));
        }
        System.out.println();
        return count;
    }
    
    public void extractChapters(final Manga manga) throws Exception{
            Document doc = Jsoup.parse(new URL(manga.url), 15000);

            List<Chapter> list = 
                    doc.select("#chapters .chlist li div").stream()
                    .flatMap(e -> e.children().stream())
                    .filter(e -> e.tagName().matches("h\\d"))
                    .map(elm -> {
                        Element elm2 = elm.getElementsByClass("tips").get(0);
                        String url = elm2.absUrl("href");
                        url = url.endsWith("html") ? url : url + (url.endsWith("/") ? "1.html" : "/1.html");
                        String[] splits = url.split("/|\\\\");
                        Elements titles = elm.select(".title.nowrap");
                        String title = titles.isEmpty() ? "" : titles.get(0).text();
                        String numberS = splits[splits.length - 2];
                        String volume = splits[splits.length - 3];
                        volume = volume.matches("v\\d+") ? volume : "vUnknown"; 
                        numberS = numberS.charAt(0) == 'c' ? numberS.substring(1) : numberS;
                        double number = Double.parseDouble(numberS);

                        String volume_temp = volume;
                        String url_temp = url;

                        Chapter ch = manga.getChapter(number, () -> new Chapter(volume_temp, number, title, url_temp));
                        return ch;
                    }).collect(Collectors.toList()  );

            Collections.reverse(list);
            manga.addChapters(list);
    }
}
