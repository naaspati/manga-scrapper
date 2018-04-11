package sam.manga.scrapper.manga.parts;
import static sam.console.ANSI.red;

import sam.manga.scrapper.extras.Errors;
import sam.manga.scrapper.units.Chapter;

public class Chapter2 extends Chapter {
    private static final long serialVersionUID = -2338302187435283639L;

    public Chapter2(int mangaid, String volume, double number, String title, String url) {
        super(mangaid, volume, number, title, url);
    }
    
    @Override
    public void setPageCount(int count) {
        if(count == 0) {
            System.out.println(red("  page-count = 0: ")+url);
            Errors.CHAPTER_PAGES_ZERO.addError(mangaid, number, null, url);
        }
        
        if(pages != null && pages.length != 0 && pages.length < count) {
            System.out.println(red("page-count mismatch, old: ")+pages.length+red(", new: ")+count);
            return;
        }
        super.setPageCount(count);
    }
}