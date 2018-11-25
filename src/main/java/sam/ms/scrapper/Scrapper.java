package sam.ms.scrapper;

import java.io.IOException;

import sam.manga.scrapper.scrappers.impl.ScrapperCached;
import sam.myutils.System2;

public class Scrapper extends ScrapperCached {
	public static final String URL_COLUMN = sam.manga.scrapper.Scrapper.urlColumn();
	
	public Scrapper() throws IOException {
		super(System2.lookup("BASE_URL", "http://fanfox.net/"));
	}
}
