package sam.manga.downloader;

import static sam.console.ANSI.FINISHED_BANNER;
import static sam.console.ANSI.createBanner;
import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.console.VT100.erase_down;
import static sam.console.VT100.save_cursor;
import static sam.console.VT100.unsave_cursor;
import static sam.swing.SwingUtils.copyToClipBoard;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import mangafoxscrapper.scrapper.Scrapper2;
import sam.fileutils.FilesUtils;
import sam.internetutils.InternetUtils;
import sam.manga.newsamrock.chapters.ChapterUtils;
import sam.manga.newsamrock.converter.ConvertChapter;
import sam.manga.scrapper.extras.Errors;
import sam.manga.scrapper.extras.FailedPage;
import sam.manga.scrapper.manga.parts.Manga2;
import sam.manga.scrapper.scrappers.AbstractScrapper;
import sam.myutils.MyUtils;
import sam.string.StringUtils;
import sam.tsv.Tsv;

// lots of refactoring needed
public class Downloader {
    private final List<ConvertChapter> chapters = new ArrayList<>();
    private final List<FailedPage> failedPages = Collections.synchronizedList(new LinkedList<>());
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    private final String format;
    private final AbstractScrapper scrapper = Scrapper2.getInstance();
    
    private final List<Callable<String>> callables = new ArrayList<>();
    
    public Downloader(Path root, Map<Integer, Manga2> mangasMap){
        format = green("(%s/"+mangasMap.values().size()+")  ")+"%d %s  %s";
        
        System.out.println("\n\n"+createBanner("downloading")+"\n");

        int[] progress = {1};
        mangasMap.forEach((manga_id, manga) -> {
            System.out.printf(format, progress[0]++, manga_id, manga.mangaName, "\n");

            if(manga.isEmpty()){
                System.out.println(red("no chapters scrapped\n"));
                return;
            }

            Path mangaDir = root.resolve(manga.dirName);

            int count[] = {1};
            final String progressFormat = yellow("\n\n%d")+ " / "+green(manga.chaptersCount());

            manga.forEach((chap_num, chapter) -> {
                Path folder = mangaDir.resolve(ChapterUtils.makeChapterFileName(chap_num, chapter.title, manga.mangaName));
                
                ConvertChapter cc = new ConvertChapter(chapter.mangaid, chapter.number, chapter.title, folder, folder);
                chapters.add(cc);

                erase_down();
                System.out.print("  "+yellow(folder.getFileName())+"  ");
                save_cursor();
                System.out.printf(progressFormat, count[0]++);
                unsave_cursor();

                if(chapter.isEmpty()){
                    System.out.println("NO Pages scrapped ");
                    return;
                }

                try {
                    Files.createDirectories(folder);
                } catch (IOException e) {
                    System.out.println("\n"+red("Failed to create dir: ")+folder+"\t"+e+"\n");
                    Errors.DOWNLOADER.addError(manga.id, chap_num, e, "Failed to create dir: ", folder);
                    return;
                }

                System.out.print("("+chapter.getPageCount()+"): ");
                callables.clear();

                chapter.forEach(page -> {
                    Path target = folder.resolve(String.valueOf(page.order));
                    if(Files.exists(target))
                        System.out.print(page.order+" ");
                    else {
                        callables.add(() -> {
                            try {
                                InternetUtils.download(new URL(page.imageUrl), target);
                                System.out.print(page.order+" ");    
                            } catch (IOException e) {
                                System.out.print(red(page.order+" "));
                                failedPages.add(new FailedPage(target, page, chapter, manga, e));
                            }
                            return null;
                        });    
                    }
                });
                if(!callables.isEmpty()) {
                    try {
                        executorService.invokeAll(callables);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                System.out.println();
            });
            System.out.println();
        });

        Runnable finishTask = new FinishTask();

        Thread finishThread = new Thread(finishTask);
        Runtime.getRuntime().addShutdownHook(finishThread);

        if(!failedPages.isEmpty())
            attemptFailedPages();

        executorService.shutdown();
        Runtime.getRuntime().removeShutdownHook(finishThread);
        finishTask.run();
    }
    
    private volatile boolean firstAttempt = true;
    private void attemptFailedPages() {
        System.out.println("\n\n"+red("FAILED: ")+failedPages.size());

        while(!failedPages.isEmpty() && JOptionPane.showConfirmDialog(null, "try downloading again?") == JOptionPane.YES_OPTION) {
            callables.clear();
            for (FailedPage fp : failedPages) {
                callables.add(() -> {
                    try {
                        InternetUtils.download(new URL(firstAttempt ? fp.page.imageUrl : scrapper.getImageUrl(fp.page.pageUrl)), fp.target);
                        System.out.println(green(fp.manga.dirName)+" -> "+yellow(fp.chapter.title) +" -> "+ fp.page.order+" ");
                        failedPages.remove(fp);
                    } catch (IOException|NullPointerException e) {
                        System.out.print(green(fp.manga.dirName)+" -> "+yellow(fp.chapter.title) +" -> "+ red(fp.page.order)+" ");
                    }
                    return null;
                });
            }
            try {
                executorService.invokeAll(callables);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(!failedPages.isEmpty())
                System.out.println("\n\n"+red("FAILED: ")+failedPages.size());
            else
                break;
            firstAttempt = false;
        }
    }

    private class FinishTask implements Runnable {
        @Override
        public void run() {
                executorService.shutdownNow();

                Consumer<Path> delete = path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        System.out.println(red("deleting failed: ")+path +"  error:"+e);
                    }
                };
                
                Path failedPagesPath = Paths.get("failed-pages.tsv");

                if(!failedPages.isEmpty()) {
                    Tsv tsv = new Tsv("path", "url", "page url", "error");

                    for (FailedPage fp : failedPages)
                        tsv.rows().add(fp.target.toString(), fp.page.pageUrl, fp.page.imageUrl, MyUtils.exceptionToString(fp.error));

                    tsv.rows().add("");
                    tsv.rows().add("");
                    
                    StringBuilder sbb = new StringBuilder();

                    failedPages.stream()
                    .collect(Collectors.groupingBy(fp -> fp.manga.id, TreeMap::new,  Collectors.mapping(fp -> fp.chapter.number, Collectors.toCollection(TreeSet::new))))
                    .forEach((manga_id, chaps_nums) -> {
                        sbb.append(manga_id).append(' ');
                        chaps_nums.forEach(c -> sbb.append(StringUtils.doubleToString(c)).append(' '));
                        sbb.append(' ');
                    });
                    
                    tsv.rows().add(sbb.toString());
                    
                    try {
                        tsv.save(failedPagesPath);
                        System.out.println(green(failedPagesPath + "  created"));
                        System.out.println(red("failed manga-chaps: ")+sbb);
                    } catch (IOException e) {
                        System.out.println(red("failed to write: ")+failedPagesPath+"  error:"+e);
                    }
                }
                else 
                    delete.accept(failedPagesPath);

                System.out.println(FINISHED_BANNER);
                Map<Path, List<Path>> grouped = chapters.stream().filter(Objects::nonNull).map(ConvertChapter::getTarget).collect(Collectors.groupingBy(p -> p.subpath(0,p.getNameCount() - 1))); 
                
                System.out.println();
                StringBuilder sb = new StringBuilder();
                grouped.forEach((s,t) -> {
                    yellow(sb, s.getFileName()).append('\n');
                    t.forEach(z -> sb.append("  ").append(z.getFileName()).append('\n'));
                });
                
                Tsv tsv = ConvertChapter.toTsv(chapters);
                try {
                    Path path = Paths.get("chapters.tsv");
                   if(Files.exists(path)) {
                       Path p = FilesUtils.findPathNotExists(path);
                       Files.move(path, p);
                       System.out.println(path+"  moved to -> "+p);
                   } 
                    tsv.save(path);
                    System.out.println(green("chapters.tsv created"));
                } catch (IOException e) {
                    System.out.println(red("failed to save: chapters.tsv")+MyUtils.exceptionToString(e));
                }
                copyToClipBoard(grouped.keySet().stream().map(Path::toString).collect(Collectors.joining("\n")));
        }
    }
}
