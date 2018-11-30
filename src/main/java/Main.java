
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import sam.ms.Downloader;
import sam.ms.extras.Utils;

/**
 * 
 * this is less OOP oriented 
 * this commit each entry to database (dude thats not efficient)
 * 
 * @author Sameer
 *
 */
public class Main {
	public static final double VERSION = Double.parseDouble(System.getenv("APP_VERSION"));
	
    public static void main(String[] args) throws Exception {

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
        
        List<String> argsList = Stream.of(args).skip(1).collect(Collectors.toList());
        Utils.setPrintFilter(argsList.remove("--print-filter"));
        
        if(CMD.TSV.test())
        	new Downloader().tsv(argsList);
        else if(CMD.MCHAP.test())
        	new Downloader().mchap(argsList);
        else {
        	System.out.println(red("failed to recognize command: ")+Arrays.toString(args));
            CMD.showHelp();
        }
    }
}

