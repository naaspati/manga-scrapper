package sam.ms.extras;

import static sam.api.config.InjectorKeys.DRY_RUN;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import sam.api.FileUtils;
import sam.api.ShutdownHooks;
import sam.nopkg.EnsureSingleton;

@Singleton
public class FileUtilsImpl implements FileUtils {
	private static final EnsureSingleton singleton = new EnsureSingleton();
	{ singleton.init(); }
	
	private final List<Path> CREATED_DIRS = new ArrayList<>();
	private final boolean dryRun;

	@Inject
	public FileUtilsImpl(@Named(DRY_RUN) boolean dryRun, ShutdownHooks hooks) {
		this.dryRun = dryRun;
		if (!dryRun) {
			hooks.addShutdownHook(() -> {
				synchronized (FileUtilsImpl.this) {
					CREATED_DIRS.sort(Comparator.comparingInt(Path::getNameCount).reversed());
					CREATED_DIRS.forEach(f -> f.toFile().delete());
					CREATED_DIRS.stream().map(Path::getParent).distinct().forEach(f -> f.toFile().delete());
				}
			});
		}
	}

	@Override
	public void deleteIfExists(Path path) throws IOException {
		if (dryRun)
			return;
		Files.deleteIfExists(path);
	}

	@Override
	public void createDirectories(Path dir) throws IOException {
		if (dryRun)
			return;
		Files.createDirectories(dir);

		synchronized (this) {
			CREATED_DIRS.add(dir);
		}
	}
}
