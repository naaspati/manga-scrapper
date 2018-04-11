import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoublePredicate;

import mangafoxscrapper.scrapper.Scrapper2;
import sam.manga.downloader.Downloader;
import sam.manga.scrapper.extras.Errors;
import sam.manga.scrapper.manga.parts.ChapterFilter;
import sam.manga.scrapper.manga.parts.Manga2;

public class UrlScraper {
    public UrlScraper(List<String> args, Map<Integer, Manga2> mangasMap) {
        System.out.println("\n");

        HashMap<String, List<String>> temp = new HashMap<>();

        List<String> curList = null;
        for (String s : args) {
            if(s.startsWith("http")){
                curList = new ArrayList<>();
                temp.put(s, curList);
            }
            else if(curList != null)
                curList.add(s);
        }
        temp.forEach((s,t) -> System.out.println(s+"\n\t"+t));
        System.out.println("\n");
        int[] progress = {1};

        List<Integer> mangaIds = new ArrayList<>();
        Scrapper2 scrapper = Scrapper2.getInstance();

        temp.forEach((url,rangeList) -> {
            String name = url.charAt(url.length() - 1) == '/' ? url.substring(url.lastIndexOf('/', url.length() - 2) + 1, url.length() - 1) : url.substring(url.lastIndexOf('/') + 1, url.length());
            Manga2 manga = new Manga2(name.hashCode(), name, name, url);
            mangasMap.put(manga.id, manga);
            mangaIds.add(manga.id);

            System.out.println(yellow(manga));

            scrapper.extractChapters(manga);

            if(manga.isEmpty()){
                System.out.println(red("  no chapters extracted"));
                Errors.NO_CHAPTERS_SCRAPPED.addError(manga.id, null, null, manga.id, manga.dirName);
                return;
            }

            int total = manga.chaptersCount();
            DoublePredicate range = rangeList == null || rangeList.isEmpty() ? null : rangeList.stream().map(s -> new ChapterFilter(s).getTester()).reduce((s,t) -> s.or(t)).get();

            if(range != null)
                manga.removeChapterIf(c -> !range.test(c.number));

            System.out.printf("\tchap_count: %s, queued: %s\n",total, manga.chaptersCount());

            scrapper. processChapters((progress[0]++)+" / "+temp.size(), manga);
            System.out.println();
        });

        Path root = Paths.get("downloaded");
        try {
            Files.createDirectories(root);
            new Downloader(root, mangasMap);
        } catch (IOException e) {
            System.out.println(red("failed to create dir: ")+root);
        }
    }
}
