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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.DoublePredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JOptionPane;

import sam.manga.downloader.Downloader;
import sam.manga.newsamrock.SamrockDB;
import sam.manga.newsamrock.mangas.MangasMeta;
import sam.manga.scrapper.extras.Errors;
import sam.manga.scrapper.extras.IdNameView;
import sam.manga.scrapper.manga.parts.Chapter;
import sam.manga.scrapper.manga.parts.Manga;
import sam.manga.scrapper.scrappers.AbstractScrapper;
import sam.manga.scrapper.scrappers.MangaHere;
import sam.myutils.fileutils.FilesUtils;
import sam.myutils.myutils.MyUtils;
import sam.myutils.stringutils.StringUtils;
import sam.properties.myconfig.MyConfig;
import sam.sql.sqlite.SqlConsumer;
import sam.sql.sqlite.SqliteManeger;
import sam.swing.utils.SwingUtils;
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
public class Main {
    private static final double VERSION = 1.96;
    private static final Path APP_HOME = Paths.get(System.getenv("APP_HOME"));

    private final Path MANGA_FOLDER = Paths.get(MyConfig.MANGA_FOLDER);
    private final Path MISSING_CHAPTERS_PATH = Paths.get(MyConfig.MISSING_CHAPTERS_PATH);
    private final Path NEW_MANGAS_TSV_PATH = Paths.get(MyConfig.NEW_MANGAS_TSV_PATH);
    private final Path UPDATED_MANGAS_TSV_PATH = Paths.get(MyConfig.UPDATED_MANGAS_TSV_PATH);
    private final AbstractScrapper scrapper;

    public static void main(String[] args) throws ClassNotFoundException, IOException, URISyntaxException, InstantiationException, IllegalAccessException, SQLException {

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
                SwingUtils.copyToClipBoard(String.join(" ", args));
                m = new Main();
                m.mangaIdChapterNumberScrapper(argsList);
            }
        }
        else if(CMD.DB.test()){
            new IdNameView();
        }
        else{
            System.out.println(red("failed to recognize command: ")+Arrays.toString(args));
            CMD.showHelp();
        }
    }

    private static void clean() {
        System.out.println("\n");
        File[] paths = new File(".").listFiles(f -> f.isFile() && !f.getName().equals("ms.bat"));

        Path downloads = Paths.get("downloaded");

        if((paths == null || paths.length == 0) && Files.notExists(downloads)) {
            System.out.println(green("nothing to clean"));
            System.exit(0);
        }
        for (File p : paths)
            System.out.println(yellow(p.getName()));

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

        if(paths != null && paths.length != 0) {
            for (File f : paths)
                System.out.println(f+"  "+(f.delete() ? green("success") : red("failed")));
        }
        delete.accept(files, false);
        delete.accept(dirs, true);
    }
    private void notifyme() {
        boolean errorOccured = Stream.of(Errors.values()).anyMatch(e -> e.getErrors() != null);

        try {
            if(errorOccured){
                System.out.println(red(createUnColoredBanner("ERRORS")));

                StringBuilder sbb = new StringBuilder();
                Errors.failedMangaIdChapterNumberMap.forEach((mangaid, chapNums) -> {
                    sbb.append(mangaid).append(' ');
                    chapNums.stream()
                    .filter(Objects::nonNull)
                    .mapToDouble(d -> d)
                    .forEach(d -> (d == (int)d ? sbb.append((int)d) : sbb.append(d)).append(' '));
                });
                sbb.append("\n\n");

                Errors.failedMangaIdChapterNumberMap.keySet()
                .stream().map(mangasMap::get)
                .forEach(m -> {
                    if(m == null)
                        return;
                    sbb.append(m.id).append(Errors.separator)
                    .append(m.name).append(Errors.separator)
                    .append(m.url).append('\n');
                });

                sbb.append("\n\n");

                String data = Stream.of(Errors.values())
                        .filter(e -> e.getErrors() != null)
                        .reduce(sbb, (sb,error) -> sb.append("\n--------------------------\n").append(error).append('\n').append(error.getErrors()).append('\n'), StringBuilder::append).toString();

                Files.write(Paths.get("errors.txt"), data.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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

    final Map<Integer, Manga> mangasMap;

    @SuppressWarnings("unchecked")
    public Main() throws IOException, ClassNotFoundException {
        Path p = Paths.get("working_backup.dat");
        Map<Integer, Manga> map = null;

        if(Files.exists(p)){
            try(ObjectInputStream oos = new ObjectInputStream(Files.newInputStream(p, StandardOpenOption.READ))) {
                map = (Map<Integer, Manga>) oos.readObject();
            } 
        }
        mangasMap = map == null ? new LinkedHashMap<>() : map;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> saveWorkingBackup()));
        scrapper = new MangaHere(); //TODO
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
                    Errors.CHAPTER.addError(manga.id, chap_num, e, chap.url);
                    Errors.addFailedMangaIdChapterNumber(manga.id, chap_num);
                }
                chap.update();
                erase_down();
            }    
        });
    }

    void extractChapters(final Manga manga){
        try {
            scrapper.extractChapters(manga);
        } catch (Exception e) {
            Errors.MANGA.addError(manga.id, null, e, "Chapter extraction failed");
        }
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

            Map<Integer, sam.manga.newsamrock.chapters.Chapter> mangaIdLastChapMap = null;

            try(SamrockDB samrock = new SamrockDB()) {
                mangaIdLastChapMap = samrock.getAllLastChapters(); 
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
                    System.out.println(red("  no chapters extracted"));
                    Errors.NO_CHAPTERS_SCRAPPED.addError(manga.id, null, null, manga_id, manga.name);
                    mangasMap.remove(manga_id);
                    continue;
                }   

                int ttl = manga.chaptersCount();
                sam.manga.newsamrock.chapters.Chapter lastChap = mangaIdLastChapMap.get(manga.id);
                manga.removeChapterIf(c -> {
                    if(lastChap == null || c.number <= lastChap.getNumber())
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
                    Errors.CHAPTER.addError(null, null, null, b);
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
        Path db = Paths.get(scrapper.getUrlColumnName()+".db");

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

                        chapter.forEach(page -> {
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
        final Map<Integer, Set<String>> idMissingChapMap = new LinkedHashMap<>();

        if(data.size() == 1)
            idMissingChapMap.put(currentId = Integer.parseInt(data.get(0)), new LinkedHashSet<>());
        else {
            for (String s : data) {
                if(s.indexOf('_') < 0 && 
                        s.indexOf('-') < 0 && 
                        s.indexOf('.') < 0 && 
                        s.length() > 3){
                    currentId = Integer.parseInt(s);

                    if(idMissingChapMap.containsKey(currentId))
                        continue;

                    idMissingChapMap.put(currentId, new LinkedHashSet<>());
                }
                else if(currentId > 0)
                    idMissingChapMap.get(currentId).add(s);
            }
        }
        if(idMissingChapMap.isEmpty()){
            System.out.println(red("no manga id(s) found"));
            return;
        }

        try(SamrockDB  db = new SamrockDB()) {
            Collection<Integer> ids = idMissingChapMap.keySet();
            Map<Integer, String> mangaurls = db.getUrls(ids, scrapper.getUrlColumnName());

            if(mangaurls.values().stream().anyMatch(Objects::isNull)) {
                Tsv t = new Tsv(MangasMeta.MANGA_ID, MangasMeta.MANGA_NAME, "url");

                mangaurls.values().removeIf(Objects::nonNull);//TODO

                db.selectMangasByIdIterate(mangaurls.keySet(), 
                        rs -> {t.addRow(rs.getString(MangasMeta.MANGA_ID), rs.getString(MangasMeta.MANGA_NAME));}, 
                        MangasMeta.MANGA_ID, MangasMeta.MANGA_NAME);

                System.out.println(red("\nmissing urls"));
                System.out.print(String.format(yellow("%-10s%-10s%n"), "manga_id", "manga_name"));
                t.forEach(r -> System.out.printf("%-10s%-10s%n", r.get(0), r.get(1)));
                t.save(Paths.get("missing-urls.tsv"));

                System.out.println("\nmissing-urls.tsv created");

                System.exit(0);
            }

            Map<Integer, sam.manga.newsamrock.chapters.Chapter> lastChap = db.getLastChapters(ids);
            SqlConsumer<ResultSet> consumer = rs -> {
                Integer id = rs.getInt(MangasMeta.MANGA_ID);
                String dirName = rs.getString(MangasMeta.DIR_NAME);

                mangasMap.computeIfAbsent(id, _id -> new Manga(_id, dirName, mangaurls.get(_id)));

                if(idMissingChapMap.get(id).isEmpty())
                    idMissingChapMap.get(id).add(StringUtils.doubleToString(lastChap.get(id).getNumber()).concat("_"));
            }; 

            db.selectMangasByIdIterate((Collection<Integer>)idMissingChapMap.keySet(), consumer, MangasMeta.MANGA_ID, MangasMeta.DIR_NAME);
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

    private List<Integer> scrap(Map<Integer, Set<String>> idMissingChapMap) {
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
                System.out.println(red("  no chapters extracted"));
                Errors.NO_CHAPTERS_SCRAPPED.addError(manga.id, null, null, manga_id, manga.name);
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
                .filter(p -> p == null || p.imageUrl == null)
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
                System.out.println(red("  no chapters extracted"));
                Errors.NO_CHAPTERS_SCRAPPED.addError(manga.id, null, null, manga.id, manga.name);
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

