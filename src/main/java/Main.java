
import static sam.api.config.InjectorKeys.APP_DATA_DIR;
import static sam.api.config.InjectorKeys.DEBUG;
import static sam.api.config.InjectorKeys.DRY_RUN;
import static sam.api.config.InjectorKeys.MANGA_DIR;
import static sam.api.config.InjectorKeys.SAMROCK_DB;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.inject.Named;
import javax.inject.Singleton;

import org.codejargon.feather.Provides;

import com.almworks.sqlite4java.SQLiteException;

import sam.api.FileUtils;
import sam.api.ShutdownHooks;
import sam.api.config.ConfigService;
import sam.api.config.OutProvider;
import sam.api.store.Store;
import sam.console.ANSI;
import sam.di.FeatherInjector;
import sam.di.Injector;
import sam.di.InjectorProvider;
import sam.internetutils.InternetUtils;
import sam.manga.Env;
import sam.manga.api.scrapper.ScrapperException;
import sam.ms.Downloader;
import sam.ms.GsonStore;
import sam.ms.extras.FileUtilsImpl;
import sam.ms.extras.ShutdownTasks;
import sam.myutils.MyUtilsException;
import sam.myutils.System2;

public class Main {
	public static void main(String[] args) throws Exception {
		new Main().doMain(args);
	}

	private boolean dryRun, debug;
	private Path dbPath, appDataDir, mangaRootDir;

	private ConfigService configService;
	private PrintStream out;

	private void doMain(String[] args0) throws InstantiationException, IllegalAccessException, ClassNotFoundException,
			IOException, SQLiteException, ScrapperException {
		List<String> args = args0.length == 0 ? Collections.emptyList() : new ArrayList<>(Arrays.asList(args0));

		this.dryRun = get(args, "--dry-run");
		this.debug = get(args, "--debug");

		Map<String, String> argsProps = ConfigService.propertiesFromArgs(args);

		if (args.isEmpty())
			System.out.println(red("Invalid using of command: zero number of commands\n"));

		if (args.isEmpty() || has(args, "-h", "--help")) {
			CMD.showHelp(System.out);
			System.exit(0);
		}

		if (has(args, "-v", "--version")) {
			System.out.println(yellow("version: " + Double.parseDouble(System2.lookup("APP_VERSION")) + "\n\n"));
			System.exit(0);
		}

		String filename = System2.lookup("config.file", "config.file.properties").trim();
		Path configFilePath = Paths.get(filename);
		if (Files.notExists(configFilePath)) {
			System.out.println("config.file notfound: " + filename);
		}
		this.out = OutProvider.create(System2.lookup("sam.console.log", argsProps.get("sam.console.log")));
		this.configService = ConfigService.defaultImpl(configFilePath, argsProps, debug ? out : null);

		appDataDir = getPath(APP_DATA_DIR, "app_data");
		mangaRootDir = getPath(MANGA_DIR, Env.MANGA_DIR);
		dbPath = getPath(SAMROCK_DB, Env.SAMROCK_DB);

		CMD.testAgainst = args.remove(0);

		if (CMD.FORMAT.test())
			formatDownload();
		/*
		 * else if(CMD.TSV.test()) new Downloader().tsv(args);
		 */
		else if (CMD.MCHAP.test())
			injector().instance(Downloader.class).mchap(args);
		else {
			out.println(red("failed to recognize command: ") + args);
			CMD.showHelp(out);
		}
	}

	private Path getPath(String key, String defaultValue) throws FileNotFoundException {
		String s = configService.get(key, defaultValue);
		if (s == null)
			throw new IllegalStateException("\"" + key + "\" env not set");

		Path path = Paths.get(s);
		if (Files.notExists(path))
			throw new FileNotFoundException("\"" + key + "\", file/dir not found: " + path);
		return path;
	}

	private boolean get(List<String> args, String key) {
		return args.remove(key) ? true : System2.lookupBoolean(key);
	}

	private Injector injector;

	private Injector injector() {
		if (injector == null) {
			injector = Injector.init(new FeatherInjector(InjectorProvider.detectAndAdd(this)));
		}

		return injector;
	}

	@Provides
	ShutdownHooks shutdownHooks(ShutdownTasks t) {
		return t;
	}

	@Provides
	FileUtils fileUtils(FileUtilsImpl t) {
		return t;
	}
	
	@Provides
	PrintStream out() {
		return out;
	}

	@Provides
	ConfigService cs() {
		return configService;
	}
	@Provides
	@Singleton
	Store store(GsonStore store) {
		return store;
	}

	@Provides
	@Named(DEBUG)
	boolean debug() {
		return debug;
	}

	@Provides
	@Named(DRY_RUN)
	boolean dryRun() {
		return dryRun;
	}

	@Provides
	@Named(APP_DATA_DIR)
	Path appDataDir() {
		return appDataDir;
	}

	@Provides
	@Named(SAMROCK_DB)
	Path dbPath() {
		return dbPath;
	}

	@Provides
	@Named(MANGA_DIR)
	Path mangaRootDir() {
		return mangaRootDir;
	}

	Scanner scanner;

	private void formatDownload() {
		scanner = new Scanner(System.in);
		final String url = line("formattable url: ", true, false);

		if (url.isEmpty()) {
			out.println(ANSI.red("Empty input"));
			System.exit(0);
		}

		if (url.equals(String.format(url, "11111"))) {
			out.println(ANSI.red("url not formattable"));
			System.exit(0);
		}

		InternetUtils iu = new InternetUtils();

		while (true) {
			String download_url = null;
			try {
				String s = line("enter: ", false, true);

				download_url = String.format(url, s);
				out.println(ANSI.yellow("downloading: ") + download_url);
				Path p = iu.download(download_url);
				out.println(ANSI.yellow("downloaded: ") + p.getFileName());

				while (true) {
					String target = line("save to(type --skip to skip): ", true, true);
					if (target.equals("--skip")) {
						out.println(ANSI.red("skipped move"));
						break;
					}

					try {
						Files.move(p, Paths.get(target), StandardCopyOption.REPLACE_EXISTING);
						;
						out.println(ANSI.green("moved"));
						break;
					} catch (Exception e) {
						out.println(ANSI.red("failed: ") + e);
					}
				}
			} catch (Exception e) {
				if (download_url != null)
					out.println(ANSI.red("failed download: ") + download_url + "  errror: "
							+ MyUtilsException.append(new StringBuilder(), e, true));
				else
					e.printStackTrace(out);
			}
		}
	}

	private String line(String msg, boolean trim, boolean nonEmpty) {
		if (nonEmpty) {
			while (true) {
				out.print(ANSI.yellow(msg));
				String s = trim ? scanner.nextLine().trim() : scanner.nextLine();

				if (s.isEmpty())
					out.println(ANSI.red("Empty input"));
				else
					return s;
			}
		}

		out.print(ANSI.yellow(msg));
		return trim ? scanner.nextLine().trim() : scanner.nextLine();
	}

	private boolean has(List<String> args, String... find) {
		boolean b = false;
		for (String s : find)
			b = args.remove(s) || b;

		return b;
	}
}
