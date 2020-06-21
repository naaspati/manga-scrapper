package sam.ms;

import static sam.api.config.InjectorKeys.DRY_RUN;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import com.carrotsearch.hppc.IntObjectScatterMap;

import sam.api.ShutdownHooks;
import sam.api.store.Store;
import sam.api.store.StoreException;
import sam.ms.entities.Manga;
@Singleton
public class MangaList implements Iterable<Manga> {
	private final List<Manga> mangas;
	private final IntObjectScatterMap<Manga> mangasMap = new IntObjectScatterMap<>();

	@Inject
	@SuppressWarnings("unchecked")
	MangaList(Provider<ShutdownHooks> shutdownHooks, Store storeFactory, @Named(DRY_RUN) boolean dryRun, PrintStream out) throws StoreException {
		mangas = new ArrayList<>((List<Manga>) storeFactory.read());
		mangas.forEach(m -> {
			mangasMap.put(m.getMangaId(), m);
			m.init();
		});
		
		if(!dryRun) {
			shutdownHooks.get().addShutdownHook(() -> {
				try {
					storeFactory.save(mangas);
				} catch (StoreException e) {
					e.printStackTrace(out);
				}
			});			
		}
	}
	
	public Manga get(int manga_id) {
		return mangasMap.get(manga_id);
	}
	
	public Manga add(Manga m) {
		Objects.requireNonNull(m);
		Manga old = mangasMap.get(m.getMangaId()); 
		if(old == null) {
			mangasMap.put(m.getMangaId(), m);
			mangas.add(m);
			return m;
		} else {
			return old; 
		}
	}
	
	@Override
	public void forEach(Consumer<? super Manga> action) {
		mangas.forEach(action);
	}
	
	@Override
	public Iterator<Manga> iterator() {
		return mangas.iterator();
	}
}
