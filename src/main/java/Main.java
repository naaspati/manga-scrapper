
import static sam.console.ANSI.FINISHED_BANNER;
import static sam.console.ANSI.createUnColoredBanner;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import sam.fileutils.FileOpenerNE;
import sam.manga.scrapper.extras.Errors;
import sam.manga.scrapper.extras.Utils;
import sam.manga.scrapper.manga.parts.Manga;
import sam.myutils.MyUtilsCmd;
import sam.swing.SwingUtils;

/**
 * 
 * this is less OOP oriented 
 * this commit each entry to database (dude thats not efficient)
 * 
 * @author Sameer
 *
 */
public class Main {
    private static final double VERSION = 1.994;
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
        if(CMD.VERSION.test()){
            System.out.println(yellow("version: "+VERSION+"\n\n"));
            return;
        }

        Class.forName("org.sqlite.JDBC");
        Main m = null;

        List<String> argsList = Stream.of(args).skip(1).collect(Collectors.toList());
        Utils.setPrintFilter(argsList.remove("--print-filter"));

        if(CMD.TSV.test()){
            int sizeIndex = argsList.indexOf("size");
            int limit = -1;

            if(sizeIndex >= 0){
                if(sizeIndex >= argsList.size() - 1){
                    System.out.println("no value set for argument : "+red("size"));
                    return;
                }
                try {
                    limit = Integer.parseInt(argsList.get(sizeIndex + 1));
                } catch (NumberFormatException e) {
                    System.out.println("failed to parse values: "+red(argsList.get(sizeIndex + 1))+" for option "+red("size"));
                    return;
                }
            }
            m = new Main();
            System.out.println("size: "+limit);
            new TsvScrapper(m.mangasMap, limit < 0 ? Integer.MAX_VALUE : limit);
        }
        else if(CMD.MCHAP.test()){
            if(argsList.size() < 1)
                System.out.println(red("invalid count of commands: ")+Arrays.toString(args));
            else{
                SwingUtils.copyToClipBoard(String.join(" ", args));
                m = new Main();
                new MangaIdChapterNumberScrapper(argsList, m.mangasMap).start();
                System.out.println(FINISHED_BANNER);
            }
        }
        else{
            System.out.println(red("failed to recognize command: ")+Arrays.toString(args));
            CMD.showHelp();
        }
        
        if(m != null)
            m.notifyme();
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

        MyUtilsCmd.beep(5);

        if(errorOccured)
            FileOpenerNE.openFile(new File("."));
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

