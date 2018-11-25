package sam.manga.scrapper.extras;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import sam.myutils.MyUtilsException;

/**
 * use DB.addError(...)
 * @author Sameer
 *
 */
@Deprecated
public enum Errors {
    MANGA, 
    CHAPTER, 
    PAGE,
    CHAPTER_PAGES_ZERO,
    NO_CHAPTERS_SCRAPPED,
    MANGA_ID_CHAPTER_NUMBER, 
    DOWNLOADER;

    public static final char[] separator = {' ', ',', ' '}; 
    public static final Map<Integer, Set<Double>> failedMangaIdChapterNumberMap = new LinkedHashMap<>();

    private volatile StringBuilder errors;

    public synchronized void addError(Integer mangaid, Double chapterid,  Exception e, Object...msgs) {
        if(errors == null) errors = new StringBuilder();

        if(msgs != null && msgs.length != 0) {
            errors.append(mangaid).append(separator);
            for (Object s : msgs) errors.append(s).append(separator);
            errors.setLength(errors.length() - separator.length);
        }

        if(e != null)
            errors.append('\t').append(MyUtilsException.exceptionToString(e));
        errors.append('\n');

        if(chapterid != null)
            addFailedMangaIdChapterNumber(mangaid, chapterid); 
    }
    public StringBuilder getErrors() {
        return errors;
    }
    public static void addFailedMangaIdChapterNumber(Integer mangaid, Double chapterNumber) {
        Set<Double> set = failedMangaIdChapterNumberMap.get(mangaid);
        if(set == null)
            failedMangaIdChapterNumberMap.put(mangaid, set = new LinkedHashSet<>());

        if(chapterNumber != null)
            set.add(chapterNumber);
    }
}