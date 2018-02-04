import static sam.console.ansi.ANSI.ANSI_CLOSE;
import static sam.console.ansi.ANSI.FINISHED_BANNER;
import static sam.console.ansi.ANSI.createBanner;
import static sam.console.ansi.ANSI.createUnColoredBanner;
import static sam.console.ansi.ANSI.cyan;
import static sam.console.ansi.ANSI.green;
import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;
import static sam.console.vt100.VT100.erase_down;
import static sam.console.vt100.VT100.save_cursor;
import static sam.console.vt100.VT100.unsave_cursor;
import static sam.swing.utils.SwingUtils.showErrorDialog;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.DoublePredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JOptionPane;

import sam.console.ansi.ANSI;
import sam.db.sqlite.SqliteManeger;
import sam.manga.downloader.Downloader;
import sam.manga.scrapper.extras.ErrorKeys;
import sam.manga.scrapper.extras.IdNameView;
import sam.manga.scrapper.manga.parts.Chapter;
import sam.manga.scrapper.manga.parts.Manga;
import sam.manga.scrapper.scrappers.IScrapper;
import sam.manga.scrapper.scrappers.MangaFox;
import sam.manga.tools.MangaTools;
import sam.myutils.fileutils.FilesUtils;
import sam.myutils.myutils.MyUtils;
import sam.properties.myconfig.MyConfig;
import sam.tsv.Row;
import sam.tsv.Tsv;

/**
 * 
 * this is less OOP oriented 
 * this commit each entry to database (dude thats not efficient)
 * 
 * @author Sameer
 *
 */
public class Main{
    private static final double VERSION = 1.945;
    private static final Path APP_HOME = Paths.get(System.getenv("APP_HOME"));

    private final Path MANGA_FOLDER = Paths.get(MyConfig.MANGA_FOLDER);
    private final Path MISSING_CHAPTERS_PATH = Paths.get(MyConfig.MISSING_CHAPTERS_PATH);
    private final Path NEW_MANGAS_TSV_PATH = Paths.get(MyConfig.NEW_MANGAS_TSV_PATH);
    private final Path UPDATED_MANGAS_TSV_PATH = Paths.get(MyConfig.UPDATED_MANGAS_TSV_PATH);
    private final IScrapper scrapper;

    public static void main(String[] args) throws ClassNotFoundException, IOException, URISyntaxException {

        if(args.length == 0){
            System.out.println(red("Invalid using of command: zero number of commands\n"));
            CMD.showHelp();
            return;
        }

        CMD.testAgainst = args[0];

        if(CMD.HELP.test()){
            CMD.showHelp();
            return;
        }
        if(CMD.CLEAN.test()){
            clean();
            return;
        }
        if(CMD.VERSION.test()){
            System.out.println(yellow("version: "+VERSION+"\n\n"));
            return;
        }

        Class.forName("org.sqlite.JDBC");
        Main m = null;

        List<String> argsList = Stream.of(args).skip(1).collect(Collectors.toList());

        if(CMD.TSV.test()){
            int sizeIndex = argsList.indexOf("size");
            int size = -1;

            if(sizeIndex >= 0){
                if(sizeIndex >= argsList.size() - 1){
                    System.out.println("no value set for argument : "+red("size"));
                    return;
                }
                try {
                    size = Integer.parseInt(argsList.get(sizeIndex + 1));
                } catch (NumberFormatException e) {
                    System.out.println("failed to parse values: "+red(argsList.get(sizeIndex + 1))+" for option "+red("size"));
                    return;
                }
            }

            m = new Main();
            System.out.println("size: "+size);
            m.tsvExtractor(size);
        }
        else if(CMD.URL.test()){
            if(argsList.isEmpty()){
                System.out.println("no url specified");
                return;
            }

            new Main().urlScraper(argsList);
        }
        else if(CMD.MCHAP.test()){
            if(argsList.size() < 1)
                System.out.println(red("invalid count of commands: ")+Arrays.toString(args));
            else{
                m = new Main();
                m.mangaIdChapterNumberScrapper(argsList);
            }
        }
        else if(CMD.DB.test()){
            new IdNameView();
        }
        else if(CMD.DB_UPDATE_CHAPTERS.test()) {
            Properties p = new Properties();
            p.load(Files.newInputStream(APP_HOME.resolve("config.properties")));

            Path dbPath = Paths.get(p.getProperty("downloader.db.path"));

            if(Files.notExists(dbPath)) {
                System.out.println(red("mangafox.db not found: ")+dbPath.toAbsolutePath().normalize());
                System.exit(0);
            }
            if(argsList.isEmpty()) {
                System.out.println(red("no chapter id provided"));
                System.exit(0);
            }

            Predicate<String> filter = s -> !s.matches("-?\\d+");
            Object[] badData = argsList.stream().filter(filter).toArray();
            if(badData.length != 0)
                System.out.println(red("bad chapters id(s): ")+Arrays.toString(badData));
            argsList.removeIf(filter);

            if(argsList.isEmpty()) {
                System.out.println(red("no chapter id found"));
                System.exit(0);
            }

            m = new Main();
            m.dbUpdateChapters(argsList, dbPath.toAbsolutePath().normalize().toString());
        }
        else{
            System.out.println(red("failed to recognize command: ")+Arrays.toString(args));
            CMD.showHelp();
        }
    }

    private static void clean() {
        System.out.println("\n");
        List<Path> paths = Stream.of("general-failed.txt","failed-pages.txt", "errors.txt","working_backup.dat","mangafox.db","failedlist.txt").map(Paths::get).filter(Files::exists).collect(Collectors.toList());

        Path downloads = Paths.get("downloaded");

        if(paths.isEmpty() && Files.notExists(downloads)) {
            System.out.println(green("nothing to clean"));
            System.exit(0);
        }
        paths.forEach(p -> System.out.println(yellow(p.getFileName())));

        List<Path> files  = new ArrayList<>(), 
                dirs = new ArrayList<>();

        if(Files.exists(downloads)) {
            System.out.println(yellow("\n-------------------------"));
            try {
                int count = downloads.getNameCount();
                BiConsumer<Path, Boolean> fill = (path, isDir) -> {
                    int d = path.getNameCount() - count;
                    if(d == 0) {
                        System.out.println(green(" "+path));
                        return;
                    }

                    StringBuilder sb = new StringBuilder(isDir ? " \u001b[32m" : " ");
                    for (int i = 0; i < d; i++) sb.append('|').append(' ');
                    sb.append(path.getFileName());

                    sb.append(isDir ? ANSI_CLOSE : "");

                    System.out.println(sb.toString());
                };

                Files.walkFileTree(downloads, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        fill.accept(dir, true);
                        dirs.add(dir);
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        files.add(file);
                        fill.accept(file, false);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                showErrorDialog("failed to list download contents", e);
                System.exit(0);
            }
        }

        if(JOptionPane.showConfirmDialog(null, "sure to delete?") != JOptionPane.YES_OPTION)
            return;

        BiConsumer<List<Path>, Boolean> delete = (list, isDirs) -> {
            if(list.isEmpty())
                return;

            if(isDirs)
                Collections.sort(list, (a,b) -> Integer.compare(b.getNameCount(), a.getNameCount()));

            for (Path p : list) {
                try {
                    Files.deleteIfExists(p);
                    System.out.println(green(p.getFileName()));
                } catch (Exception e) {
                    System.out.println(red(p.getFileName())+"  "+e);
                }
            }
            System.out.println();
        };

        System.out.println(yellow("\n\n-------------------------\nDELETING\n"));

        delete.accept(paths, false);
        delete.accept(files, false);
        delete.accept(dirs, true);
    }
    private void notifyme() {
        boolean errorOccured = ERRORS.values().stream().anyMatch(l -> !l.isEmpty());

        try {
            if(errorOccured){
                System.out.println(red(createUnColoredBanner("ERRORS")));
                StringBuilder b = new StringBuilder();
                ERRORS.forEach((s,t) -> b.append(t.isEmpty() ? "" : (red(s)+"\n  "+String.join("\n  ", t)+"\n\n")));

                System.out.println(b);
                Files.write(Paths.get("errors.txt"), b.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            else
                Files.deleteIfExists(Paths.get("errors.txt"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println(FINISHED_BANNER);

        MyUtils.beep(5);

        if(errorOccured)
            FilesUtils.openFileNoError(new File("."));

    }

    final Map<ErrorKeys, Collection<String>> ERRORS = new LinkedHashMap<>();
    final Map<Integer, Manga> mangasMap;

    @SuppressWarnings("unchecked")
    public Main() throws IOException, ClassNotFoundException {
        for (ErrorKeys k : ErrorKeys.values()) ERRORS.put(k, new ArrayList<>());

        Path p = Paths.get("working_backup.dat");
        Map<Integer, Manga> map = null;

        if(Files.exists(p)){
            try(ObjectInputStream oos = new ObjectInputStream(Files.newInputStream(p, StandardOpenOption.READ))) {
                map = (Map<Integer, Manga>) oos.readObject();
            } 
        }
        mangasMap = map == null ? new LinkedHashMap<>() : map;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> saveWorkingBackup()));
        scrapper = new MangaFox();
    }

    private void saveWorkingBackup() {
        if(mangasMap.isEmpty())
            return;

        Path p = Paths.get("working_backup.dat");

        try(ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {
            oos.writeObject(mangasMap);
            oos.flush();
        } catch (IOException e) {
            System.err.println("Failed to save working_backup.dat");
            e.printStackTrace();
        }
    }

    private void dbUpdateChapters(List<String> chapterIds, String dbPath) {
        try(SqliteManeger c = new SqliteManeger(dbPath, true);
                PreparedStatement insert = c.prepareStatement("UPDATE Pages SET url = ? WHERE id = ?");
                ) {
            Map<String, List<String[]>> mangaIdChapDataMap = 
                    c.executeQueryAndMapResultSet(
                            chapterIds.stream().collect(Collectors.joining(",", "SELECT id, manga_id, number, title FROM Chapters WHERE id IN(", ")")), 
                            rs -> MyUtils.array(rs.getString("id"), rs.getString("manga_id"), rs.getString("number"), rs.getString("title")))
                    .peek(s -> chapterIds.remove(s[0])).collect(Collectors.groupingBy(s -> s[1]));

            if(!chapterIds.isEmpty())
                System.out.println(red("No Data Found For chapter id(s): ")+chapterIds);

            if(mangaIdChapDataMap.isEmpty() || mangaIdChapDataMap.values().stream().allMatch(List::isEmpty)) {
                System.out.println(red("no valid chapter id(s) to process"));
                return;
            }
            Map<String, String> mangaIdNameMap = new HashMap<>();
            c.executeQueryAndIterateResultSet(
                    mangaIdChapDataMap.keySet().stream().collect(Collectors.joining(",", "SELECT id, name FROM Mangas WHERE id IN(", ")")), 
                    rs -> mangaIdNameMap.put(rs.getString("id"), rs.getString("name")));

            Map<String, List<String[]>> chapIdPageDataMap = 
                    c.executeQueryAndMapResultSet(mangaIdChapDataMap.values().stream().flatMap(List::stream).map(s -> s[0]).collect(Collectors.joining(",", "SELECT id, chapter_id, _order, page_url FROM Pages WHERE chapter_id IN(", ")")), 
                            rs -> MyUtils.array(rs.getString("id"), rs.getString("chapter_id"), rs.getString("_order"), rs.getString("page_url")))
                    .peek(s -> chapterIds.remove(s[0])).collect(Collectors.groupingBy(s -> s[1]));

            int progress[] = {1}; 
            String mangaFormat = yellow("(%s/"+mangaIdNameMap.size()+")  ")+cyan("%s  ")+yellow("Queued: ")+"%s%n";
            String chapFormat = yellow("  (%s/%s) ")+green("%s %s")+yellow(" (%s)")+":";
            int[] failed = {0};

            System.out.println(ANSI.cyan("\nmanga_ids: ")+mangaIdChapDataMap.keySet());
            System.out.println(ANSI.cyan("\nchapter_ids: ")+mangaIdChapDataMap.values().stream().flatMap(List::stream).map(s -> s[0]).collect(Collectors.toList()));
            System.out.println("\n");

            while(true) {
                mangaIdChapDataMap.forEach((manga_id, chapData) -> {
                    System.out.printf(mangaFormat, progress[0]++, mangaIdNameMap.get(manga_id), chapData.size());
                    int p[] = {1};
                    chapData.removeIf(cd -> {
                        List<String[]> pageData = chapIdPageDataMap.get(cd[0]);
                        System.out.printf(chapFormat, p[0]++, chapData.size(), cd[2].replaceFirst("\\.0$", ""), cd[3], pageData.size());
                        pageData.removeIf(pd -> {
                            try {
                                String imgUrl = scrapper.getImageUrl(pd[3]);
                                insert.setString(1, imgUrl);
                                insert.setString(2, pd[0]);
                                insert.addBatch();
                                System.out.print(" "+pd[2]);
                                return true;
                            } catch (Exception e) {
                                ERRORS.get(ErrorKeys.PAGE).add(pd[3]+"\t"+getError(e));
                            }    
                            System.out.print(" "+red(pd[2]));
                            failed[0]++;
                            return false;
                        });
                        System.out.println();
                        return pageData.isEmpty();
                    });
                });

                mangaIdChapDataMap.values().removeIf(List::isEmpty);

                if(failed[0] == 0)
                    break;
                System.out.println(red("\n\nfailed: ")+failed[0]);
                failed[0] = 0;
                if(JOptionPane.showConfirmDialog(null, "retry?") != JOptionPane.YES_OPTION)
                    break;
            }
            System.out.println(green("\n\nExecuted: ")+insert.executeBatch().length);
            c.commit();
            System.out.println(ANSI.FINISHED_BANNER);
            MyUtils.beep(5);
            System.out.println(yellow("db updated: ")+dbPath);
        } catch (SQLException | InstantiationException | IllegalAccessException | ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }
    }

    void processChapters(final String mangaProgress, Manga manga) {
        processChapters(mangaProgress, manga, -1);
    }
    private void processChapters(final String mangaProgress, Manga manga, int limit) {
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
                    ERRORS.get(ErrorKeys.CHAPTER).add(chap.url+"\t"+getError(e));
                }
                chap.update();
                erase_down();
            }    
        });
    }

    void extractChapters(final Manga manga){
        int attempt = 10;
        try {
            scrapper.extractChapters(manga);
        } catch (Exception e) {}

        while(attempt >= 0 && manga.isEmpty()) attempt--;

    }
    private String getError(Exception e) {
        return  e + Stream.of(e.getStackTrace()).map(t -> String.valueOf(t.getLineNumber())).collect(Collectors.joining(",", "(", ")"));
    }

    public void tsvExtractor(final int extractSize) {
        Tsv newTsv = null, updatedTsv = null;
        Map<String, TreeMap<Double, String>> missingChapterMap = null;

        try {
            if(Files.exists(NEW_MANGAS_TSV_PATH))
                newTsv = Tsv.parse(NEW_MANGAS_TSV_PATH);
            else
                System.out.println(red("newManga List not exists"));

            if(Files.exists(UPDATED_MANGAS_TSV_PATH) && Files.exists(MISSING_CHAPTERS_PATH)){
                updatedTsv = Tsv.parse(UPDATED_MANGAS_TSV_PATH);
                missingChapterMap = FilesUtils.readObjectFromFile(MISSING_CHAPTERS_PATH);
            }
            else
                System.out.println(red("no updates"));
        } catch (Exception e) {
            showErrorDialog("Error with list files", e);
            return;
        }

        int size = (newTsv == null ? 0 : newTsv.size()) + (updatedTsv == null ? 0 : updatedTsv.size());

        if(size == 0){
            System.out.println(yellow("No Data to extract"));
            return;
        }

        String mangaFormat = green("\n(%d/%d)  ")+cyan("Manga: %s");

        if(updatedTsv != null && missingChapterMap != null){
            System.out.println(createBanner("Updated Mangas"));
            int count = 0;
            final int total = updatedTsv.size();

            Map<Integer, Double> mangaIdLastChapMap = new HashMap<>();

            try(SqliteManeger c = new SqliteManeger(MyConfig.SAMROCK_DB, true)) {
                c.executeQueryAndIterateResultSet("SELECT * FROM LastChap", rs -> {
                    mangaIdLastChapMap.put(rs.getInt("manga_id"), Double.parseDouble(MangaTools.extractChapterNumber(rs.getString("last_chap_name"))));
                });
            } catch (SQLException | InstantiationException | IllegalAccessException | IOException | ClassNotFoundException e) {
                System.out.println("failed to open samrock connection: "+MyConfig.SAMROCK_DB);
                e.printStackTrace();
                return;
            }
            for (Row row : updatedTsv) {
                TreeMap<Double, String> map = missingChapterMap.get(row.get("manga_id"));

                System.out.printf(mangaFormat, ++count, total, row.get("manga_name"));

                final Integer manga_id = Integer.parseInt(row.get("manga_id"));
                Manga manga = mangasMap.putIfAbsent(manga_id, new Manga(manga_id, row));
                manga = manga != null ? manga : mangasMap.get(manga_id);

                extractChapters(manga);

                if(manga.isEmpty()){
                    System.out.println(red("no chapters extracted"));
                    mangasMap.remove(manga_id);
                    continue;
                }   

                int ttl = manga.chaptersCount();
                Double lastChap = mangaIdLastChapMap.get(manga.id);
                manga.removeChapterIf(c -> {
                    if(lastChap == null || c.number <= lastChap)
                        return !map.containsKey(c.number);
                    return false;
                });

                if(manga.isEmpty()){
                    System.out.println(red("no chapters to download"));
                    mangasMap.remove(manga_id);
                    continue;
                }

                System.out.printf("\tchap_count: %s, missing_count: %s\n", ttl, manga.chaptersCount());

                manga.forEach((chap_num, chap) -> map.remove(chap_num));

                if(!map.isEmpty()){
                    StringBuilder b = red(new StringBuilder(),"-- chapters not found -- \n");

                    map.forEach((s,t) -> b.append(s).append(' ').append(t).append('\n'));
                    b.append('\n');

                    System.out.println(b);

                    b.insert(0, row);
                    ERRORS.get(ErrorKeys.CHAPTER).add(b.toString());
                }

                processChapters(count+"/"+total, manga);
            }
        }
        if(newTsv != null){
            System.out.println("\n\n"+createBanner("New Mangas"));
            int count = 0;
            final int total = newTsv.size();

            for (Row row : newTsv) {
                System.out.printf(mangaFormat, ++count, total, row.get("manga_name"));

                final Integer manga_id = Integer.parseInt(row.get("manga_id"));
                Manga manga = mangasMap.putIfAbsent(manga_id, new Manga(manga_id, row));
                manga = manga != null ? manga : mangasMap.get(manga_id);

                extractChapters(manga);

                int ttl = manga.chaptersCount();
                System.out.printf("\tchap_count: %s, missing_count: %s\n", ttl, extractSize < 0 || extractSize > ttl ? ttl :  extractSize );
                processChapters(count+"/"+total, manga, extractSize);
            }
        }
        createdatabase();
        notifyme();
    }

    private void createdatabase() {
        Path db = Paths.get("mangafox.db");

        try {
            Files.deleteIfExists(db);
        } catch (IOException e) {
            System.err.println(red("Faiedl to delete "+db));
            e.printStackTrace();
            return;
        }
        try (SqliteManeger c = new SqliteManeger(db.toString(), true)) {
            c.createDefaultStatement();

            String pagesSql = 
                    "CREATE TABLE `Pages` ("+
                            "   `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE ,"+
                            "   `chapter_id`    INTEGER NOT NULL,"+
                            "   `manga_id`  INTEGER NOT NULL,"+
                            "   `_order`    INTEGER NOT NULL,"+
                            "   `status`    TEXT NOT NULL DEFAULT 'UNTOUCHED',"+
                            "   `page_url`  TEXT,"+
                            "   `url`   TEXT,"+
                            "   `errors`    TEXT"+
                            ");";

            String chaptersSql = 
                    "CREATE TABLE `Chapters` ("+
                            "   `id`    INTEGER NOT NULL UNIQUE PRIMARY KEY,"+
                            "   `manga_id`  INTEGER NOT NULL,"+
                            "   `volume`    TEXT,"+
                            "   `number`    REAL NOT NULL,"+
                            "   `title` TEXT,"+
                            "   `url`   TEXT NOT NULL,"+
                            "   `page_count`    INTEGER NOT NULL,"+
                            "   `status`    INTEGER NOT NULL DEFAULT 'UNTOUCHED'"+
                            ");";

            String mangasSql = "CREATE TABLE  `Mangas` ("+
                    "   `id`    INTEGER NOT NULL PRIMARY KEY UNIQUE,"+
                    "   `name`  TEXT NOT NULL UNIQUE,"+
                    "   `url`   TEXT NOT NULL UNIQUE,"+
                    "   `status`    TEXT NOT NULL DEFAULT 'UNTOUCHED'"+
                    ");";

            c.executeUpdate(chaptersSql);
            c.executeUpdate(mangasSql);
            c.executeUpdate(pagesSql);
            c.commit();

            final String ADD_PAGE_SQL = "INSERT INTO Pages(chapter_id, manga_id, _order, page_url, url) VALUES(?,?,?,?,?)";
            final String ADD_CHAPTER_SQL = "INSERT INTO Chapters(id, manga_id, volume, number, title, url, page_count) VALUES(?,?,?,?,?,?,?)";

            try(PreparedStatement addPage = c.prepareStatement(ADD_PAGE_SQL);
                    PreparedStatement addChapter = c.prepareStatement(ADD_CHAPTER_SQL);
                    PreparedStatement addManga = c.prepareStatement("INSERT INTO Mangas(id, name, url) VALUES(?,?,?)");) {

                mangasMap.forEach((manga_id, manga) -> {
                    try {
                        addManga.setInt(1, manga_id);
                        addManga.setString(2, manga.name);
                        addManga.setString(3, manga.url);
                        addManga.addBatch();
                    } catch (SQLException e) {
                        System.out.println(red("failed to add manga to db: ")+manga);
                        return;
                    }

                    manga.forEach((chapter_number, chapter) -> {
                        int chapterId = chapter.getHashId();
                        try {
                            addChapter.setInt(1, chapterId);
                            addChapter.setInt(2, manga_id);
                            addChapter.setString(3, chapter.volume);
                            addChapter.setDouble(4, chapter.number);
                            addChapter.setString(5, chapter.title);
                            addChapter.setString(6, chapter.url);
                            addChapter.setInt(7, chapter.getPageCount());
                            addChapter.addBatch();
                        } catch (SQLException e) {
                            System.out.println(red("failed to add chapter to db: ")+chapter);
                            return;
                        }

                        chapter.forEach((order, page) -> {
                            try {
                                addPage.setInt(1, chapterId);
                                addPage.setInt(2, manga_id);
                                addPage.setInt(3, page.order);
                                addPage.setString(4, page.pageUrl);
                                addPage.setString(5, page.imageUrl);
                                addPage.addBatch();
                            } catch (SQLException e) {
                                System.out.println(red("failed to add page to db: ")+page);
                                return;
                            }
                        });
                    });
                }); 

                System.out.println(yellow("\n Commits"));
                System.out.println("mangas: "+addManga.executeBatch().length);
                System.out.println("chapters: "+addChapter.executeBatch().length);
                System.out.println("pages: "+addPage.executeBatch().length);
            }
            c.commit();
        }catch(SQLException | InstantiationException | IllegalAccessException | ClassNotFoundException | IOException e){
            showErrorDialog("Failed to create database", e);
        }
    }
    static DoublePredicate rangeMaker(final String s){
        final int index1 = s.indexOf('-');
        final int index2 = s.indexOf('_');

        if(index1 < 0 && index2 < 0){
            return d -> d == Double.parseDouble(s);
        }

        if(index1 == 0 || index2 == 0){
            return  index1 == 0 
                    ? d -> d <= Double.parseDouble(s.substring(1)) 
                    : d -> d < Double.parseDouble(s.substring(1));
        }
        else if(index1 == s.length() - 1 || index2 == s.length() - 1){
            return index1 == s.length() - 1  
                    ? d -> d >= Double.parseDouble(s.substring(0, s.length() - 1)) 
                    : d -> d > Double.parseDouble(s.substring(0, s.length() - 1));
        }
        else{
            double n1 = Double.parseDouble(s.substring(0, index1 > 0 ? index1 : index2));
            double n2 = Double.parseDouble(s.substring((index1 > 0 ? index1 : index2) + 1, s.length()));

            return index1 > 0 
                    ? d -> n1 <= d && d <= n2
                    : d -> n1 < d && d < n2;
        }
    }

    void mangaIdChapterNumberScrapper(List<String> data) throws URISyntaxException, IOException{
        if(data.isEmpty()){
            System.out.println(red("no data input"));
            return;
        }

        int currentId = -1;
        final Map<Integer, List<String>> idMissingChapMap = new LinkedHashMap<>();

        if(data.size() == 1)
            idMissingChapMap.put(currentId = Integer.parseInt(data.get(0)), new ArrayList<>());
        else {
            for (String s : data) {
                if(s.indexOf('_') < 0 && 
                        s.indexOf('-') < 0 && 
                        s.indexOf('.') < 0 && 
                        s.length() > 3){
                    currentId = Integer.parseInt(s);

                    if(idMissingChapMap.containsKey(currentId))
                        continue;

                    idMissingChapMap.put(currentId, new ArrayList<>());
                }
                else if(currentId > 0)
                    idMissingChapMap.get(currentId).add(s);
            }
        }
        if(idMissingChapMap.isEmpty()){
            System.out.println(red("no manga id(s) found"));
            return;
        }

        try(SqliteManeger c = new SqliteManeger(MyConfig.SAMROCK_DB, true);
                ) {
            String ids = idMissingChapMap.keySet().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            Map<Integer, String> mangaurls = new HashMap<>();
            c.executeQueryAndIterateResultSet("SELECT manga_id, mangafox FROM MangaUrls WHERE manga_id IN("+ids+")", 
                    rs -> mangaurls.put(rs.getInt("manga_id"), rs.getString("mangafox")));


            Map<Integer, String> lastChap = new HashMap<>();
            c.executeQueryAndIterateResultSet("SELECT manga_id, last_chap_name FROM LastChap WHERE manga_id IN("+ids+")",
                    rs -> lastChap.put(rs.getInt("manga_id"), rs.getString("last_chap_name")));

            c.executeQueryAndIterateResultSet("SELECT manga_id, dir_name FROM MangaData WHERE manga_id IN("+ids+")", 
                    rs -> {
                        Integer id = rs.getInt("manga_id");
                        String dirName = rs.getString("dir_name");

                        mangasMap.computeIfAbsent(id, _id -> new Manga(_id, dirName, mangaurls.get(_id)));

                        if(idMissingChapMap.get(id).isEmpty())
                            idMissingChapMap.get(id).add(MangaTools.extractChapterNumber(lastChap.get(id)).concat("_"));
                    });
        } catch (SQLException | InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            showErrorDialog("Error with url extracting", e);
            return;
        }

        List<Integer> failed = new ArrayList<>();

        idMissingChapMap.forEach((id, missings) -> {
            Manga m = mangasMap.get(id);

            if(m == null){
                System.out.println(red("no manga data with id: "+id)); 
                return;
            }
            if(m.name == null || m.url == null){
                System.out.println(red(m));
                failed.add(id);
            }
            else{
                System.out.println(yellow(id + ", "+m.name));
                System.out.println("   missings: "+missings);             
            }
        });
        Path p1 = APP_HOME.resolve("-mc.log");
        Path p2 = APP_HOME.resolve("last-mc");

        if(Files.exists(p1)) {
            StringBuilder sb = new StringBuilder();
            idMissingChapMap.forEach((id, missings) -> {
                sb.append(" ").append(id);
                missings.forEach(s -> sb.append(" ").append(s));
            });
            sb.append('\n');

            byte[] b1 = sb.toString().getBytes();
            byte[] b2 = Files.readAllBytes(p2);

            if(!Arrays.equals(b1, b2)) {
                Files.write(p2, b1, StandardOpenOption.TRUNCATE_EXISTING);
                Path temp = p1.resolveSibling("temp");
                OutputStream os = Files.newOutputStream(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                sb.insert(0, ']');
                sb.insert(0, LocalDateTime.now());
                sb.insert(0, '[');

                os.write(sb.toString().getBytes());
                Files.copy(p1, os);
                os.flush();
                os.close();
                Files.delete(p1);
                Files.move(temp, p1);
                System.out.println(yellow("-mc.log saved"));
            }
        }
        if(!failed.isEmpty()){
            System.out.println(red("\n\nbad data -> ")+failed);
            idMissingChapMap.keySet().removeAll(failed);
            if(idMissingChapMap.isEmpty()) {
                System.out.println(red("ALL Bad Data"));
                return;
            }
        }

        scrap(idMissingChapMap);

        new Downloader(MANGA_FOLDER, idMissingChapMap.keySet(), mangasMap, scrapper);
        notifyme();
    }

    private List<Integer> scrap(Map<Integer, List<String>> idMissingChapMap) {
        System.out.println("\n\n"+createBanner("scrapping"));

        int progress[] = {1};

        final int total = idMissingChapMap.size();
        String format = green("(%s/"+total+")  ")+"%d %s  %s";

        int count[] = {0};
        ArrayList<Integer> failed = new ArrayList<>();

        idMissingChapMap.forEach((manga_id, missings) -> {
            Manga manga = mangasMap.get(manga_id);
            final String url = manga.url;
            final String dir = manga.name;

            System.out.printf(format, progress[0]++, manga_id, dir == null ? red("null") : dir, url == null ? red("null") : url);

            if(url == null || dir == null){
                System.out.println("\n"+red("SKIPPED:  url == null || dir == null"));
                return;
            }

            if(missings.isEmpty()){
                System.out.println("no missings");
                return;
            }

            DoublePredicate missingsPredicate = missings.stream()
                    .map(Main::rangeMaker)
                    .reduce((d1, d2) -> d1.or(d2)).get();

            extractChapters(manga);

            if(manga.isEmpty()){
                System.out.println(red("no chapters extracted"));
                failed.add(manga_id);
                return;
            }   

            int total1 = manga.chaptersCount();

            manga.removeChapterIf(c -> !missingsPredicate.test(c.number));

            System.out.printf("\tchap_count: %s, missing_count: %s\n",total1, manga.chaptersCount());

            processChapters((++count[0])+"/"+total, manga);
            System.out.println();
        });


        long failedPages = 
                idMissingChapMap.keySet().stream()
                .map(mangasMap::get)
                .filter(Objects::nonNull)
                .flatMap(Manga::chapterStream)
                .flatMap(Chapter::pageStream)
                .filter(p -> p.imageUrl == null)
                .count();

        if(failedPages > 0) {
            System.out.println(red("\nfailed count: ")+failedPages);
            if(JOptionPane.showConfirmDialog(null, "try scrapping again?") == JOptionPane.YES_OPTION)
                return scrap(idMissingChapMap);
        }
        return failed;
    }
    private void urlScraper(List<String> args) {
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


        temp.forEach((url,rangeList) -> {
            String name = url.charAt(url.length() - 1) == '/' ? url.substring(url.lastIndexOf('/', url.length() - 2) + 1, url.length() - 1) : url.substring(url.lastIndexOf('/') + 1, url.length());
            Manga manga = new Manga(name.hashCode(), name, url);
            mangasMap.put(manga.id, manga);
            mangaIds.add(manga.id);

            System.out.println(yellow(manga));

            extractChapters(manga);

            if(manga.isEmpty()){
                System.out.println(red("no chapters extracted"));
                return;
            }

            int total = manga.chaptersCount();

            DoublePredicate range = rangeList == null || rangeList.isEmpty() ? null : rangeList.stream().map(Main::rangeMaker).reduce((s,t) -> s.or(t)).get();

            if(range != null)
                manga.removeChapterIf(c -> !range.test(c.number));

            System.out.printf("\tchap_count: %s, queued: %s\n",total, manga.chaptersCount());

            processChapters((progress[0]++)+" / "+temp.size(), manga);
            System.out.println();
        });

        Path root = Paths.get("downloaded");
        try {
            Files.createDirectories(root);
            new Downloader(root, mangaIds, mangasMap, scrapper);
        } catch (IOException e) {
            System.out.println(red("failed to create dir: ")+root);
        }
        notifyme();

    }

}

