package sam.ms.extras;

import static sam.console.ANSI.red;

import java.nio.file.Path;
import java.nio.file.Paths;

import sam.config.MyConfig;
import sam.myutils.System2;

public class Utils {
	public static final Path APP_DATA = Paths.get(System2.lookup("APP_DATA"));
	public static final Path MANGA_DIR = Paths.get(MyConfig.MANGA_DIR);
	
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

