import static sam.console.ANSI.createBanner;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.swing.SwingUtils.showErrorDialog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import mangafoxscrapper.scrapper.Scrapper;
import mangafoxscrapper.scrapper.Scraps;
import sam.config.MyConfig;
import sam.manga.scrapper.manga.parts.Chapter;
import sam.manga.scrapper.manga.parts.Manga;
import sam.manga.scrapper.manga.parts.Page;
import sam.sql.sqlite.SQLiteDB;
import sam.tsv.Tsv;

public class TsvScrapper {
    private final Path NEW_MANGAS_TSV_FILE = Paths.get(MyConfig.NEW_MANGAS_TSV_FILE);
    private final Path UPDATED_MANGAS_TSV_FILE = Paths.get(MyConfig.UPDATED_MANGAS_TSV_FILE);
    private Scrapper scrapper;

    public TsvScrapper(Map<Integer, Manga> mangasMap, int limit) throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException, IOException {
        Tsv newTsv = null, updatedTsv = null;

        try {
            if(Files.exists(NEW_MANGAS_TSV_FILE))
                newTsv = Tsv.parse(NEW_MANGAS_TSV_FILE);
            else
                System.out.println(red("newManga List not exists"));

            if(Files.exists(UPDATED_MANGAS_TSV_FILE))
                updatedTsv = Tsv.parse(UPDATED_MANGAS_TSV_FILE);
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
        
        Scraps scraps = null;

        if(updatedTsv != null){
            System.out.println(createBanner("Updated Mangas"));
            MangaIdChapterNumberScrapper mc = new MangaIdChapterNumberScrapper(updatedTsv.stream().map(r -> r.get("manga_id")).collect(Collectors.toList()), mangasMap);
            mc.start(updatedTsv.size() + Optional.ofNullable(newTsv).map(Tsv::size).orElse(0));
            scraps = mc.getScraps();
            this.scrapper = mc.scrapper();
        }
        if(newTsv != null){
        	scrapper = scrapper != null ? scrapper : new Scrapper();
        	
            System.out.println("\n\n"+createBanner("New Mangas"));

            List<Manga> mangas = newTsv
                    .stream()
                    .map(row -> {
                        final Integer manga_id = Integer.parseInt(row.get("manga_id"));
                        Manga manga = mangasMap.putIfAbsent(manga_id, new Manga(row, scrapper.urlColumn()));
                        return manga != null ? manga : mangasMap.get(manga_id);
                    })
                    .collect(Collectors.toList());
            
            scraps = scraps != null ? scraps : new Scraps(scrapper, mangas, Integer.MAX_VALUE, mangas.size());
            scraps.setMangas(mangas);
            scraps.scrap();
        }
        // this is to be remove, since Downloader uses on-the-fly scrapping+download createdatabase(mangasMap);
    }

    private void createdatabase(Map<Integer, Manga> mangasMap) {
        Path db = Paths.get(scrapper.urlColumn()+".db");

        try {
            Files.deleteIfExists(db);
        } catch (IOException e) {
            System.err.println(red("Faiedl to delete "+db));
            e.printStackTrace();
            return;
        }
        try (SQLiteDB c = new SQLiteDB(db.toString())) {
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

                    for (Chapter chapter : manga) {
                        int chapterId = chapter.getHashId();
                        try {
                            addChapter.setInt(1, chapterId);
                            addChapter.setInt(2, manga_id);
                            addChapter.setString(3, chapter.getVolume());
                            addChapter.setDouble(4, chapter.getNumber());
                            addChapter.setString(5, chapter.getTitle());
                            addChapter.setString(6, chapter.getUrl());
                            addChapter.setInt(7, chapter.nonNullPageCount());
                            addChapter.addBatch();
                        } catch (SQLException e) {
                            System.out.println(red("failed to add chapter to db: ")+chapter);
                            continue;
                        }

                        for(Page page: chapter) {
                            try {
                                addPage.setInt(1, chapterId);
                                addPage.setInt(2, manga_id);
                                addPage.setInt(3, page.getOrder());
                                addPage.setString(4, page.getPageUrl());
                                addPage.setString(5, page.getImageUrl());
                                addPage.addBatch();
                            } catch (SQLException e) {
                                System.out.println(red("failed to add page to db: ")+page);
                                continue;
                            }
                        }
                    }
                }); 

                System.out.println(yellow("\n Commits"));
                System.out.println("mangas: "+addManga.executeBatch().length);
                System.out.println("chapters: "+addChapter.executeBatch().length);
                System.out.println("pages: "+addPage.executeBatch().length);
            }
            c.commit();
        }catch(SQLException | InstantiationException | IllegalAccessException | ClassNotFoundException e){
            showErrorDialog("Failed to create database", e);
        }
    }

}
