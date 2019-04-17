package sam.ms.extras;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.LoggerFactory;

import sam.config.MyConfig;
import sam.myutils.System2;

public class Utils {
	public static final Path APP_DATA = Paths.get(System2.lookup("APP_DATA"));
	public static final Path MANGA_DIR = Paths.get(MyConfig.MANGA_DIR);
	public static final boolean DRY_RUN = System2.lookupBoolean("DRY_RUN", false);
	public static final boolean DEBUG = System2.lookupBoolean("DEBUG", LoggerFactory.getLogger(Utils.class).isDebugEnabled());
	private static final List<Runnable> shutdownTasks = new ArrayList<>();
	private static final List<Path> CREATED_DIRS = new ArrayList<>();
	
	private static final Object lock = new Object(); 
	
	static {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			synchronized (lock) {
				shutdownTasks.forEach(Runnable::run);
				
				CREATED_DIRS.sort(Comparator.comparingInt(Path::getNameCount).reversed());
				CREATED_DIRS.forEach(f -> f.toFile().delete());
				CREATED_DIRS.stream()
				.map(Path::getParent)
				.distinct()
				.forEach(f -> f.toFile().delete());
			}
		}));
	}
	
    private Utils() {}

	public static boolean debug() {
		return DEBUG;
	}
	public static boolean dryRun() {
		return DRY_RUN;
	}

	public static void deleteIfExists(Path path) throws IOException {
		if(DRY_RUN) 
			return;
		Files.deleteIfExists(path);
	}

	public static void createDirectories(Path dir) throws IOException {
		if(DRY_RUN) 
			return;
		Files.createDirectories(dir);
		
		synchronized (lock) {
			CREATED_DIRS.add(dir);
		}
	}
	public static void addShutdownHook(Runnable runnable) {
		synchronized (lock) {
			shutdownTasks.add(runnable);
		}
	}
}

