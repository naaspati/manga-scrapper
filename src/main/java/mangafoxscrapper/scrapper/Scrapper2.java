package mangafoxscrapper.scrapper;

import static sam.console.ANSI.cyan;
import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.console.VT100.erase_down;
import static sam.console.VT100.save_cursor;
import static sam.console.VT100.unsave_cursor;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoublePredicate;
import java.util.stream.DoubleStream;

import javax.swing.JOptionPane;

import sam.console.ANSI;
import sam.manga.newsamrock.SamrockDB;
import sam.manga.newsamrock.chapters.ChaptersMeta;
import sam.manga.scrapper.extras.Errors;
import sam.manga.scrapper.extras.Utils;
import sam.manga.scrapper.manga.parts.ChapterFilter;
import sam.manga.scrapper.manga.parts.Manga2;
import sam.manga.scrapper.scrappers.Scrapper;
import sam.manga.scrapper.scrappers.ScrapperListener;
import sam.manga.scrapper.units.Chapter;
import sam.manga.scrapper.units.Manga;
import sam.manga.scrapper.units.Page;
import sam.string.StringBuilder2;

public class Scrapper2 extends Scrapper implements ScrapperListener {
    private static volatile Scrapper2 instance;

    public static Scrapper2 getInstance() {
        if (instance == null) {
            synchronized (Scrapper2.class) {
                if (instance == null)
                    instance = new Scrapper2();
            }
        }
        return instance;
    }
    
   private int totalMangas = 0;
    
    public Scrapper2() {
        super(name());
    }
    private static String name() {
        String name = System.getProperty("scrapper"); 
        if(name == null)
            name = System.getenv("scrapper");
        if(name == null)
            throw new IllegalStateException("no scrapper property nor scrapper environment variable specied");
        
        return name;
    }
    public void extractChapters(Manga manga) {
        try {
            super.extractChapters(manga, this);    
        } catch (Exception e) {
            Errors.MANGA.addError(manga.id, null, e, "Chapter extraction failed");
        }
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

    public void processChapters(final String mangaProgress, Manga manga) {
        processChapters(mangaProgress, manga, Integer.MAX_VALUE);
    }
    public void processChapters(final String mangaProgress, Manga manga, int limit) {
        this.limit = limit;
        super.processChapters(manga, this);
    }
    
    public List<Integer> scrap(Map<Integer, Manga2> mangasMap) {
        totalMangas = mangasMap.size();
        String firstLineFormat = new StringBuilder2()
                .green("(%s/"+totalMangas+")").ln()
                .repeat(' ', 5).yellow("id: ").append("%s")
                .yellow(",  name: ").append("%s").ln()
                .repeat(' ', 5).yellow("url: ").append("%s").ln()
                .toString();

        String chapCountFormat = new StringBuilder2().repeat(' ', 5).yellow("chap_count: ").append("%s").yellow(", missing_count: ").append("%s").ln().toString();

        int count[] = {0};
        ArrayList<Integer> failed = new ArrayList<>();

        mangasMap.forEach((manga_id, manga) -> {
            final String url = manga.url;
            final String dir = manga.dirName;

            System.out.printf(firstLineFormat, ++mangaProgress, manga_id, stringOf(dir), stringOf(url));

            if(url == null || dir == null){
                System.out.println("\n"+red("SKIPPED:  url == null || dir == null"));
                return;
            }
            extractChapters(manga);

            if(manga.isEmpty()){
                System.out.println(red("  no chapters extracted"));
                Errors.NO_CHAPTERS_SCRAPPED.addError(manga.id, null, null, manga_id, manga.dirName);
                failed.add(manga_id);
                return;
            }   

            int total1 = manga.chaptersCount();
            DoublePredicate cf = ((Manga2)manga).getFilter().getTester();

            manga.removeChapterIf(c -> !cf.test(c.number));

            System.out.printf(chapCountFormat,total1, manga.chaptersCount());

            if(Utils.isPrintFilter())
                System.out.println(ANSI.yellow("     filter: ")+((Manga2)manga).getFilter());

            processChapters((++count[0])+"/"+totalMangas, manga);
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

    protected String stringOf(String s) {
        return s == null ? red("null") : s;
    }
    
    int mangaProgress = 0, chapterProgress = 0;    
    String nextChapterFormat, finalProgressFormat;
    int limit;
    
    private final StringBuilder sb = new StringBuilder();
    private final Formatter  formatter = new Formatter(sb);
    private String format(String format, Object...args) {
        sb.setLength(0);
        formatter.format(format, args);
        return sb.toString();
    }
    
    // 1 -> manga.chaptersCount();
    private final String nextChapFormatBase = green("  (%%s/%s) ")+yellow("Chapter: ")+"%%s %%s";
    private final String totalProgessFormatBase = "\n\n\n"+yellow("chapter: ")+"%%s/%s"+ cyan("  |  ")+ yellow("manga: ")+"%s/%s";
    
    @Override
    public void startedMangaScrapping(Manga manga) {
        chapterProgress = 0;
        int chaps = manga.chaptersCount();
        
        nextChapterFormat = format(nextChapFormatBase, manga.chaptersCount());
        finalProgressFormat = format(totalProgessFormatBase, chaps, mangaProgress, totalMangas); 
    }
    @Override
    public void startedChapterScrapping(Manga manga, Chapter chap){
        chapterProgress++;
        System.out.printf(nextChapterFormat, chapterProgress, chap.number, chap.title);
        save_cursor();
        System.out.printf(finalProgressFormat, chapterProgress);
        unsave_cursor();
    }
    @Override
    public void skippedCompletedChapter(Manga manga, Chapter chap) {
        System.out.printf(nextChapterFormat, ++chapterProgress, chap.number, chap.title+yellow("   SKIPPED\n"));
    }
    @Override
    public boolean continueScrapping(Double chapterNumber, Chapter chap) {
        return limit >= chapterProgress;
    }
    @Override
    public void failedScrappingPages(Manga manga, Chapter chap, Double chap_num, Exception error){
        Errors.CHAPTER.addError(manga.id, chap_num, error, chap.url);
        Errors.addFailedMangaIdChapterNumber(manga.id, chap_num);
    }
    @Override
    public void chapterScrapped(Manga arg0, Chapter arg1) {
        erase_down();
    }
    private static final String pagesCountFormat = cyan("(")+yellow("%d")+cyan("): ");
    @Override
    public void pagesFound(Chapter chap, int total) {
        System.out.printf(pagesCountFormat, total);
    }
    @Override
    public void successPage(Chapter chap, Page page) {
        System.out.print(page.imageUrl == null ? red(page.order+" ") : page.order+" ");
    }
    @Override
    public void successPageSkipped(Chapter chap, Page page) {
        System.out.print(page.order+" ");
    }
    @Override
    public void onBadChapterNumber(Manga manga, String number, String url, NumberFormatException e) {
        Errors.CHAPTER.addError(manga.id, null, e, "Bad chapter number: "+url, url);
    }


}
