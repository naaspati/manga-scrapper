import static sam.console.ansi.ANSI.ANSI_CLOSE;
import static sam.console.ansi.ANSI.FINISHED_BANNER;
import static sam.console.ansi.ANSI.createUnColoredBanner;
import static sam.console.ansi.ANSI.green;
import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;
import static sam.swing.utils.SwingUtils.showErrorDialog;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JOptionPane;

import javafx.application.Application;
import sam.manga.scrapper.extras.Errors;
import sam.manga.scrapper.extras.IdNameView;
import sam.manga.scrapper.manga.parts.Manga;
import sam.manga.scrapper.scrappers.AbstractScrapper;
import sam.manga.scrapper.scrappers.Scrapper;
import sam.myutils.fileutils.FilesUtils;
import sam.myutils.myutils.MyUtils;
import sam.swing.utils.SwingUtils;

/**
 * 
 * this is less OOP oriented 
 * this commit each entry to database (dude thats not efficient)
 * 
 * @author Sameer
 *
 */
public class Main {
    private static final double VERSION = 1.97;
    public static final Path APP_HOME = Paths.get(System.getenv("APP_HOME"));
    final Map<Integer, Manga> mangasMap;
    
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
        if(CMD.SCRAPPERS.test()){
            System.out.println(yellow("available scrappers: \n  ")+String.join("\n  ", Scrapper.availableScrappers()));
            
            if(args.length > 1 && args[1].equalsIgnoreCase("-c")) {
                Class<? extends AbstractScrapper> s = Scrapper.getInstance().getCurrentScrapperClass();
                System.out.println(yellow("\nscrapper in use: ")+s.getSimpleName()+"   ( "+s.getName()+" )");
            }
                
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
            new TsvScrapper(m.mangasMap, size);
        }
        else if(CMD.URL.test()){
            if(argsList.isEmpty()){
                System.out.println("no url specified");
                return;
            }
            m = new Main();
            new UrlScraper(argsList, m.mangasMap);
        }
        else if(CMD.MCHAP.test()){
            if(argsList.size() < 1)
                System.out.println(red("invalid count of commands: ")+Arrays.toString(args));
            else{
                SwingUtils.copyToClipBoard(String.join(" ", args));
                m = new Main();
                new MangaIdChapterNumberScrapper(argsList, m.mangasMap);
            }
        }
        else if(CMD.DB.test())
            Application.launch(IdNameView.class, args);
        else{
            System.out.println(red("failed to recognize command: ")+Arrays.toString(args));
            CMD.showHelp();
        }
        
        if(m != null)
            m.notifyme();
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
                    .append(m.dirName).append(Errors.separator)
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
}

