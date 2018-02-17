package sam.manga.scrapper.scrappers;

import static sam.console.ansi.ANSI.createBanner;
import static sam.console.ansi.ANSI.green;
import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;
import static sam.console.vt100.VT100.erase_down;
import static sam.console.vt100.VT100.save_cursor;
import static sam.console.vt100.VT100.unsave_cursor;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoublePredicate;
import java.util.stream.DoubleStream;

import javax.swing.JOptionPane;

import org.jsoup.nodes.Document;

import sam.manga.newsamrock.SamrockDB;
import sam.manga.newsamrock.chapters.ChaptersMeta;
import sam.manga.scrapper.extras.Count;
import sam.manga.scrapper.extras.Errors;
import sam.manga.scrapper.manga.parts.Chapter;
import sam.manga.scrapper.manga.parts.ChapterFilter;
import sam.manga.scrapper.manga.parts.Manga;

public class Scrapper extends AbstractScrapper {
    private final AbstractScrapper scrapper = new MangaHere();
    private static transient Scrapper instance;

    public static Scrapper getInstance() {
        if (instance == null) {
            synchronized (Scrapper.class) {
                if (instance == null)
                    instance = new Scrapper();
            }
        }
        return instance;
    }

    private Scrapper() {
    }

    public List<Integer> scrap(Map<Integer, Manga> mangasMap) {
        System.out.println("\n\n"+createBanner("scrapping"));

        int progress[] = {1};

        final int total = mangasMap.size();
        String format = green("(%s/"+total+")  ")+"%d %s  %s";

        int count[] = {0};
        ArrayList<Integer> failed = new ArrayList<>();

        mangasMap.forEach((manga_id, manga) -> {
            final String url = manga.url;
            final String dir = manga.name;

            System.out.printf(format, progress[0]++, manga_id, dir == null ? red("null") : dir, url == null ? red("null") : url);

            if(url == null || dir == null){
                System.out.println("\n"+red("SKIPPED:  url == null || dir == null"));
                return;
            }
            extractChapters(manga);
            
            if(manga.isEmpty()){
                System.out.println(red("  no chapters extracted"));
                Errors.NO_CHAPTERS_SCRAPPED.addError(manga.id, null, null, manga_id, manga.name);
                failed.add(manga_id);
                return;
            }   

            int total1 = manga.chaptersCount();
            DoublePredicate cf = manga.getFilter().getTester();
            
            manga.removeChapterIf(c -> !cf.test(c.number));

            System.out.printf("\tchap_count: %s, missing_count: %s\n",total1, manga.chaptersCount());

            processChapters((++count[0])+"/"+total, manga);
            System.out.println();
        });


        long failedPages = 
                mangasMap.keySet().stream()
                .map(mangasMap::get)
                .filter(Objects::nonNull)
                .flatMap(Manga::chapterStream)
                .flatMap(Chapter::pageStream)
                .filter(p -> p == null || p.imageUrl == null)
                .count();

        if(failedPages > 0) {
            System.out.println(red("\nfailed count: ")+failedPages);
            if(JOptionPane.showConfirmDialog(null, "try scrapping again?") == JOptionPane.YES_OPTION)
                return scrap(mangasMap);
        }
        return failed;
    }

    public  void extractChapters(final Manga manga){
        try {
            scrapper.extractChapters(manga);
        } catch (Exception e) {
            Errors.MANGA.addError(manga.id, null, e, "Chapter extraction failed");
        }
    }
    public void processChapters(final String mangaProgress, Manga manga) {
        processChapters(mangaProgress, manga, -1);
    }
    public void processChapters(final String mangaProgress, Manga manga, int limit) {
        String format = green("  (%s/"+manga.chaptersCount()+") ")+yellow("Chapter: ")+"%s %s";
        String finalProgressFormat = "\n\n\n%s"+yellow(" | ")+"%s/"+manga.chaptersCount();
        int progress[] = {0};       

        manga.forEach((chap_num, chap) -> {
            if(limit != -1 && limit >= progress[0])
                return;

            if(chap.scrappingCompleted)
                System.out.printf(format, ++progress[0], chap.number, chap.title+yellow("   SKIPPED\n"));
            else{
                System.out.printf(format, ++progress[0], chap.number, chap.title);
                save_cursor();
                System.out.printf(finalProgressFormat, mangaProgress, progress[0]);
                unsave_cursor();
                try {
                    scrapper.extractPages(chap);
                } catch (Exception e) {
                    Errors.CHAPTER.addError(manga.id, chap_num, e, chap.url);
                    Errors.addFailedMangaIdChapterNumber(manga.id, chap_num);
                }
                chap.update();
                erase_down();
            }    
        });
    }

    public String getImageUrl(String pageUrl) throws Exception {
        return scrapper.getImageUrl(pageUrl);
    }

    public Count extractPages(Chapter chapter) throws Exception {
        return scrapper.extractPages(chapter);
    }

    public String getImageUrl(Document doc) throws Exception {
        return scrapper.getImageUrl(doc);
    }

    public String getUrlColumnName() {
        return scrapper.getUrlColumnName();
    }

    @Override
    protected int _extractPages(Chapter chapter) throws Exception {
        throw new IllegalAccessError("_extractPages(Chapter chapter)");
    }

    public Map<Integer, ChapterFilter> getMissingsFilters(List<Integer> mangaIds, SamrockDB db) throws SQLException {
        Map<Integer, DoubleStream.Builder> map = new HashMap<>();
        db.chapter().selectByMangaId(mangaIds, 
                rs -> {
                    int id = rs.getInt(ChaptersMeta.MANGA_ID);
                    DoubleStream.Builder bld = map.get(id);
                    if(bld == null)
                        map.put(id, bld = DoubleStream.builder());
                    bld.accept(rs.getDouble(ChaptersMeta.NUMBER));
                }, 
                ChaptersMeta.MANGA_ID, ChaptersMeta.NUMBER);
        
        Map<Integer, ChapterFilter> map2 = new HashMap<>();
        map.forEach((id, strm) -> map2.put(id, new ChapterFilter(strm)));
        
        return map2;
    }
}
