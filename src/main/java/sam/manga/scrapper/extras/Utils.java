package sam.manga.scrapper.extras;

public class Utils {
    private Utils() {}
    
    private static boolean printFilter = false;
    
    public static void setPrintFilter(boolean printFilter) {
        Utils.printFilter = printFilter;
    }

    public static boolean isPrintFilter() {
        return printFilter;
    }
}
