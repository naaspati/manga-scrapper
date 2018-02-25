import static sam.console.ansi.ANSI.createBanner;
import static sam.console.ansi.ANSI.cyan;
import static sam.console.ansi.ANSI.green;
import static sam.console.ansi.ANSI.red;
import static sam.console.ansi.ANSI.yellow;
import static sam.swing.utils.SwingUtils.showErrorDialog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.DoublePredicate;
import java.util.stream.Collectors;

import sam.manga.newsamrock.SamrockDB;
import sam.manga.newsamrock.mangas.MangasMeta;
import sam.manga.scrapper.extras.Errors;
import sam.manga.scrapper.manga.parts.ChapterFilter;
import sam.manga.scrapper.manga.parts.Manga;
import sam.manga.scrapper.scrappers.Scrapper;
import sam.properties.myconfig.MyConfig;
import sam.sql.sqlite.SqliteManeger;
import sam.tsv.Row;
import sam.tsv.Tsv;

public class TsvScrapper {
    private final Path NEW_MANGAS_TSV_PATH = Paths.get(MyConfig.NEW_MANGAS_TSV_PATH);
    private final Path UPDATED_MANGAS_TSV_PATH = Paths.get(MyConfig.UPDATED_MANGAS_TSV_PATH);

    public TsvScrapper(Map<Integer, Manga> mangasMap,  final int extractSize) throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException, IOException {
        Tsv newTsv = null, updatedTsv = null;

        try {
            if(Files.exists(NEW_MANGAS_TSV_PATH))
                newTsv = Tsv.parse(NEW_MANGAS_TSV_PATH);
            else
                System.out.println(red("newManga List not exists"));

            if(Files.exists(UPDATED_MANGAS_TSV_PATH))
                updatedTsv = Tsv.parse(UPDATED_MANGAS_TSV_PATH);
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
        Scrapper scrapper = Scrapper.getInstance();

        if(updatedTsv != null){
            System.out.println(createBanner("Updated Mangas"));
            int count = 0;
            final int total = updatedTsv.size();

            Map<Integer, ChapterFilter> filters = null;

            try(SamrockDB samrock = new SamrockDB()) {
                filters = Scrapper.getInstance().getMissingsFilters(updatedTsv.stream().map(r -> r.getInt(MangasMeta.MANGA_ID)).collect(Collectors.toList()), samrock); 
            }

            for (Row row : updatedTsv) {
                DoublePredicate filter = filters.get(row.getInt(MangasMeta.MANGA_ID)).getTester();

                System.out.printf(mangaFormat, ++count, total, row.get("manga_name"));

                final Integer manga_id = Integer.parseInt(row.get("manga_id"));
                Manga manga = mangasMap.putIfAbsent(manga_id, new Manga(row));
                manga = manga != null ? manga : mangasMap.get(manga_id);

                scrapper.extractChapters(manga);

                if(manga.isEmpty()){
                    System.out.println(red("  no chapters extracted"));
                    Errors.NO_CHAPTERS_SCRAPPED.addError(manga.id, null, null, manga_id, manga.mangaName);
                    mangasMap.remove(manga_id);
                    continue;
                }   

                int ttl = manga.chaptersCount();
                manga.removeChapterIf(c -> !filter.test(c.number));

                if(manga.isEmpty()){
                    System.out.println(red("no chapters to download"));
                    mangasMap.remove(manga_id);
                    continue;
                }

                System.out.printf("\tchap_count: %s, missing_count: %s\n", ttl, manga.chaptersCount());
                scrapper.processChapters(count+"/"+total, manga);
            }
        }
        if(newTsv != null){
            System.out.println("\n\n"+createBanner("New Mangas"));
            int count = 0;
            final int total = newTsv.size();

            for (Row row : newTsv) {
                System.out.printf(mangaFormat, ++count, total, row.get("manga_name"));

                final Integer manga_id = row.getInt("manga_id");
                Manga manga = mangasMap.putIfAbsent(manga_id, new Manga(row));
                manga = manga != null ? manga : mangasMap.get(manga_id);

                scrapper.extractChapters(manga);

                int ttl = manga.chaptersCount();
                System.out.printf("\tchap_count: %s, missing_count: %s\n", ttl, extractSize < 0 || extractSize > ttl ? ttl :  extractSize );
                scrapper.processChapters(count+"/"+total, manga, extractSize);
            }
        }
        createdatabase(mangasMap);
    }

    private void createdatabase(Map<Integer, Manga> mangasMap) {
        Path db = Paths.get(Scrapper.getInstance().getUrlColumnName()+".db");

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
                    "   `dir_name`  TEXT NOT NULL UNIQUE,"+
                    "   `manga_name`  TEXT NOT NULL UNIQUE,"+
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
                    PreparedStatement addManga = c.prepareStatement("INSERT INTO Mangas(id, dir_name,manga_name, url) VALUES(?,?,?)");) {

                mangasMap.forEach((manga_id, manga) -> {
                    try {
                        addManga.setInt(1, manga_id);
                        addManga.setString(2, manga.dirName);
                        addManga.setString(3, manga.mangaName);
                        addManga.setString(4, manga.url);
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

}
