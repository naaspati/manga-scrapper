
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import sam.ms.Downloader;

/**
 * 
 * this is less OOP oriented 
 * this commit each entry to database (dude thats not efficient)
 * 
 * @author Sameer
 *
 */
public class Main {
    public static void main(String[] args0) throws Exception {
    	List<String> args = args0.length == 0 ? Collections.emptyList() : new ArrayList<>(Arrays.asList(args0));
    	
    	if(args.isEmpty())
    		System.out.println(red("Invalid using of command: zero number of commands\n"));
    	
        if(args.isEmpty() || has(args, "-h", "--help")) {
            CMD.showHelp();
            System.exit(0);
        }
        
        if(has(args, "-v", "--version")){
            System.out.println(yellow("version: "+Double.parseDouble(System.getenv("APP_VERSION"))+"\n\n"));
            System.exit(0);
        }
        
        if(args.remove("--dry-run"))
        	System.setProperty("DRY_RUN", "true");
        if(args.remove("--debug"))
        	System.setProperty("DEBUG", "true");

        CMD.testAgainst = args.remove(0);
        
        if(CMD.TSV.test())
        	new Downloader().tsv(args);
        else if(CMD.MCHAP.test())
        	new Downloader().mchap(args);
        else {
        	System.out.println(red("failed to recognize command: ")+args);
            CMD.showHelp();
        }
    }

	private static boolean has(List<String> args, String...find) {
		boolean b = false;
		for (String s : find) 
			b = args.remove(s) || b;
		
		return b;
	}
}

