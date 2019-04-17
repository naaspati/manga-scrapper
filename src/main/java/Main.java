
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import sam.console.ANSI;
import sam.internetutils.InternetUtils;
import sam.ms.Downloader;
import sam.myutils.MyUtilsException;

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

		if(CMD.FORMAT.test()) 
			formatDownload();
		else if(CMD.TSV.test())
			new Downloader().tsv(args);
		else if(CMD.MCHAP.test())
			new Downloader().mchap(args);
		else {
			System.out.println(red("failed to recognize command: ")+args);
			CMD.showHelp();
		}
	}

	static Scanner scanner;
	private static void formatDownload() {
		scanner = new Scanner(System.in);
		final String url = line("formattable url: ", true, false);

		if(url.isEmpty()) {
			System.out.println(ANSI.red("Empty input"));
			System.exit(0);
		}

		if(url.equals(String.format(url, "11111"))) {
			System.out.println(ANSI.red("url not formattable"));
			System.exit(0);
		}

		InternetUtils iu = new InternetUtils();

		while(true) {
			String download_url = null;
			try {
				String s = line("enter: ", false, true);

				download_url = String.format(url, s);
				System.out.println(ANSI.yellow("downloading: ")+download_url);
				Path p  = iu.download(download_url);
				System.out.println(ANSI.yellow("downloaded: ")+p.getFileName());

				while(true) {
					String target = line("save to(type --skip to skip): ", true, true);
					if(target.equals("--skip")){
						System.out.println(ANSI.red("skipped move"));
						break;
					}
						
					try {
						Files.move(p, Paths.get(target), StandardCopyOption.REPLACE_EXISTING);;
						System.out.println(ANSI.green("moved"));
						break;
					} catch (Exception e) {
						System.out.println(ANSI.red("failed: ")+e);
					}
				}
			} catch (Exception e) {
				if(download_url != null)
					System.out.println(ANSI.red("failed download: ")+download_url+"  errror: "+MyUtilsException.append(new StringBuilder(), e, true));
				else
					e.printStackTrace();
			}
		}
	}

	private static String line(String msg, boolean trim, boolean nonEmpty) {
		if(nonEmpty) {
			while(true) {
				System.out.print(ANSI.yellow(msg));
				String s = trim ? scanner.nextLine().trim() : scanner.nextLine();

				if(s.isEmpty())
					System.out.println(ANSI.red("Empty input"));
				else
					return s;
			}
		}

		System.out.print(ANSI.yellow(msg));		
		return trim ? scanner.nextLine().trim() : scanner.nextLine();
	}

	private static boolean has(List<String> args, String...find) {
		boolean b = false;
		for (String s : find) 
			b = args.remove(s) || b;

		return b;
	}
}

