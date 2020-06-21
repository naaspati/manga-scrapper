package sam.ms.extras;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.inject.Singleton;

import sam.api.ShutdownHooks;
import sam.nopkg.EnsureSingleton;

@Singleton
public class ShutdownTasks implements ShutdownHooks {
	private static final EnsureSingleton singleton = new EnsureSingleton();
	{ singleton.init(); }
	
	private final List<Runnable> shutdownTasks = new ArrayList<>();
	
	public void addShutdownHook(Runnable runnable) {
		Objects.requireNonNull(runnable);
		
		synchronized (shutdownTasks) {
			if(shutdownTasks.isEmpty()) {
				Runtime.getRuntime().addShutdownHook(new Thread(() -> {
					synchronized (shutdownTasks) {
						shutdownTasks.forEach(Runnable::run);	
					}
				}));
			}
			
			shutdownTasks.add(runnable);
			
		}
	}
}
