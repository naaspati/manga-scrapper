package sam.ms;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import sam.api.config.ConfigService;
import sam.api.store.Store;
import sam.api.store.StoreException;
import sam.api.store.entities.meta.SManga;
import sam.ms.entities.Manga;

public class GsonStore implements Store {
	private final Path path; 
	private final PrintStream out;
	
	@Inject
	public GsonStore(ConfigService config, PrintStream out) {
		this.out = out;
		this.path = Paths.get(config.get("gson.store.file", "mangas.json"));
	}
	
	@Override
	public List<Manga> read() throws StoreException {
		if(Files.notExists(path)) {
			out.println("not found store.file: "+path);
			return Collections.emptyList();
		}
		
		try(BufferedReader reader = Files.newBufferedReader(path)) {
			return Arrays.asList(new Gson().fromJson(reader, Manga[].class));
		} catch (JsonSyntaxException | JsonIOException | IOException e) {
			throw new StoreException(e);
		}
	}

	@Override
	public void save(List<? extends SManga> mangas) throws StoreException {
		try(BufferedWriter w = Files.newBufferedWriter(path, CREATE, TRUNCATE_EXISTING)) {
			new Gson().toJson(mangas, w);
			out.println("saved: "+path);
		} catch (IOException e) {
			throw new StoreException(e);
		} 
	}
}
