package sam.manga.downloader;

import static sam.console.ansi.ANSI.FINISHED_BANNER;
import static sam.console.ansi.ANSI.createBanner;
import static sam.console.ansi.ANSI.green;
import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;
import static sam.console.vt100.VT100.erase_down;
import static sam.console.vt100.VT100.save_cursor;
import static sam.console.vt100.VT100.unsave_cursor;
import static sam.swing.utils.SwingUtils.copyToClipBoard;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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

import sam.manga.scrapper.extras.FailedPage;
import sam.manga.scrapper.manga.parts.Manga;
import sam.manga.scrapper.scrappers.IScrapper;
import sam.manga.tools.MangaTools;
import sam.myutils.internetutils.InternetUtils;
import sam.tsv.Tsv;

// lots of refactoring needed
public class Downloader {
    private final StringBuffer generalFailed = new StringBuffer();
    private final HashSet<Path> chapterFolders = new HashSet<>();
    private final List<FailedPage> failedPages = Collections.synchronizedList(new LinkedList<>());
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    private final String format;
    private final IScrapper scrapper;
    
    private final List<Callable<String>> callables = new ArrayList<>();
    
    public Downloader(Path root, Collection<Integer> mangaIds, Map<Integer, Manga> mangasMap, IScrapper scrapper){
        format = green("(%s/"+mangasMap.values().size()+")  ")+"%d %s  %s";
        this.scrapper = scrapper;
        
        System.out.println("\n\n"+createBanner("downloading")+"\n");

        int[] progress = {1};
        mangasMap.forEach((manga_id, manga) -> {
            if(!mangaIds.contains(manga_id))
                return;

            System.out.printf(format, progress[0]++, manga_id, manga.name, "\n");

            if(manga.isEmpty()){
                System.out.println(red("no chapters scrapped\n"));
                return;
            }

            Path mangaDir = root.resolve(manga.name);

            int count[] = {1};
            final String progressFormat = yellow("\n\n%d")+ " / "+green(manga.chaptersCount());

            manga.forEach((chap_num, chapter) -> {
                String name = String.valueOf(chap_num).replaceAll("\\.0+$", "").concat((chapter.title == null || chapter.title.trim().isEmpty() ? "" : " "+chapter.title));
                Path folder = mangaDir.resolve(MangaTools.formatMangaChapterName(name));
                chapterFolders.add(folder);

                erase_down();
                System.out.print("  "+yellow(folder.getFileName())+"  ");
                save_cursor();
                System.out.printf(progressFormat, count[0]++);
                unsave_cursor();

                if(chapter.isEmpty()){
                    System.out.println("NO Pages scrapped ");
                    generalFailed.append("PAGES Is NULL for Chapter: ").append(chapter).append("\n\n");
                    return;
                }

                try {
                    Files.createDirectories(folder);
                } catch (IOException e) {
                    System.out.println("\n"+red("Failed to create dir: ")+folder+"\t"+e+"\n");
                    generalFailed.append("------------------------------------------------------\n");
                    generalFailed.append(folder).append('\n');
                    chapter.forEach((order, p) -> generalFailed.append(p.pageUrl).append('\n').append(p.imageUrl).append('\n'));
                    generalFailed.append("------------------------------------------------------\n\n");
                    return;
                }

                System.out.print("("+chapter.getPageCount()+"): ");
                callables.clear();

                chapter.forEach((order, page) -> {
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
                                failedPages.add(new FailedPage(target, page, chapter, manga));
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
                        System.out.println(green(fp.manga.name)+" -> "+yellow(fp.chapter.title) +" -> "+ fp.page.order+" ");
                        failedPages.remove(fp);
                    } catch (IOException|NullPointerException e) {
                        System.out.print(green(fp.manga.name)+" -> "+yellow(fp.chapter.title) +" -> "+ red(fp.page.order)+" ");
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

                Path generalFailedPath = Paths.get("general-failed.txt");
                Consumer<Path> delete = path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        System.out.println(red("deleting failed: ")+path +"  error:"+e);
                    }
                };
                
                Path failedPagesPath = Paths.get("failed-pages.tsv");

                if(!failedPages.isEmpty()) {
                    Tsv tsv = new Tsv("path", "url", "page url");

                    for (FailedPage fp : failedPages)
                        tsv.addRow(fp.target.toString(), fp.page.pageUrl, fp.page.imageUrl);

                    tsv.addRow("");
                    tsv.addRow("");
                    
                    StringBuilder sbb = new StringBuilder();

                    failedPages.stream()
                    .collect(Collectors.groupingBy(fp -> fp.manga.id, TreeMap::new,  Collectors.mapping(fp -> fp.chapter.number, Collectors.toCollection(TreeSet::new))))
                    .forEach((manga_id, chaps_nums) -> {
                        sbb.append(manga_id).append(' ');
                        chaps_nums.forEach(c -> sbb.append(c).append(' '));
                        sbb.append(' ');
                    });
                    
                    tsv.addRow(sbb.toString());
                    
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

                if(generalFailed.length() != 0) {
                    try {
                        Files.write(generalFailedPath, generalFailed.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        System.out.println(green(generalFailedPath + "  created"));
                    } catch (IOException e) {
                        System.out.println(red("failed to write: ")+generalFailedPath+"  error:"+e);
                    }
                }
                else 
                    delete.accept(generalFailedPath);

                System.out.println(FINISHED_BANNER);
                chapterFolders.forEach(System.out::println);
                copyToClipBoard(chapterFolders.stream().filter(Objects::nonNull).map(Path::getParent).distinct().map(Path::toString).collect(Collectors.joining("\n")));
        }
        
    }
}
