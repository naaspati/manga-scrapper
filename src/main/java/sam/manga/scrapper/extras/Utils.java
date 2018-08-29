package sam.manga.scrapper.extras;

import static sam.console.ANSI.red;

public class Utils {
    private Utils() {}
    
    private static boolean printFilter = false;
    
    public static void setPrintFilter(boolean printFilter) {
        Utils.printFilter = printFilter;
    }

    public static boolean isPrintFilter() {
        return printFilter;
    }
    public static String stringOf(String s) {
        return s == null ? red("null") : s;
    }
}

