import static sam.console.ANSI.yellow;

import java.util.Formatter;

enum CMD {
    HELP("-h", "--help", ""),
    VERSION("-v", "--version", ""),
    MCHAP("-mc", "--mchap", "extract and download chapters using manga-chapters pair(s)"),
    URL("-u", "--url", "extract scrap and download using url, (range can be supplied of chapter)"),
    TSV("-t", "--tsv", "extract for data in updatedManga.tsv, newManga.tsv "),
    DB_UPDATE_CHAPTERS("-duc", "--db-chapters-update", "[chapter ids]  update page urls for given chapters"),
    CLEAN("clean", null, "clean what else?"),
    DB("db", null, "open id, name list"),
    ;

    final String cmd1, cmd2, about;
    static String testAgainst;

    private CMD(String cmd1, String cmd2, String about) {
        this.cmd1 = cmd1;
        this.cmd2 = cmd2;
        this.about = about;
    }

    boolean test(){
        return testAgainst.equalsIgnoreCase(cmd1) || testAgainst.equalsIgnoreCase(cmd2);            
    }

    public static void showHelp() {
        StringBuilder b = new StringBuilder();
        Formatter formatter = new Formatter(b);
        String format = yellow("%-7s%-15s%s%n");
        for (CMD s : CMD.values()) formatter.format(format, s.cmd1, s.cmd2 == null ? "" : s.cmd2, s.about);
        formatter.close();

        b.append("\nOPTIONS\n");
        b.append("size INTEGER   number of chapters to download (if available), (only applies to -t, --tsv command)\n");
        b.append("--log          log mode\n");

        System.out.println(b);
    }
}