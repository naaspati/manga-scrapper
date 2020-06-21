package sam.ms;

import static sam.console.ANSI.FINISHED_BANNER;
import static sam.console.ANSI.red;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;

import com.almworks.sqlite4java.SQLiteException;

import sam.manga.api.scrapper.ScrapperException;
import sam.swing.SwingClipboard;

public class Downloader {
	private final Provider<MangaIdChapterNumberScrapper> provider;
	private final PrintStream out;
	
	@Inject
	public Downloader(Provider<MangaIdChapterNumberScrapper> provider, PrintStream out) {
		this.provider = provider;
		this.out = out;
	}

	public void mchap(List<String> args) throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException, SQLiteException, ScrapperException {
		if(args.isEmpty())
            out.println(red("invalid count of commands: ")+args.toString());
        else{
        	SwingClipboard.setString(String.join(" ", args));
            provider.get().start(args);
            out.println(FINISHED_BANNER);
        }
	}
}
