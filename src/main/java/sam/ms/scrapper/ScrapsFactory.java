package sam.ms.scrapper;

import java.nio.file.Path;
import java.util.function.DoublePredicate;
import java.util.function.Function;

import sam.api.store.entities.meta.SChapter;
import sam.api.store.entities.meta.SManga;
import sam.api.store.entities.meta.SPage;
import sam.manga.api.scrapper.ScrappedChapter;
import sam.manga.api.scrapper.ScrappedPage;

public interface ScrapsFactory {
	/**
	 * return {@link Scraps#STOP_MANGA} if wish to stop the process 
	 * @return
	 */
	SChapter getChapter(SPage page);
	SManga getManga(SChapter c);
	DoublePredicate filterFor(SManga manga);
	int limitFor(SManga manga);
	SChapter newChapter(SManga manga, ScrappedChapter sc);
	SPage newPage(SChapter chapter, ScrappedPage sc);
	Function<SChapter, Path> chapterPathResolver(SManga manga);
	default Path pagePath(Path chapterPath, SPage page) {
		return chapterPath.resolve(Integer.toString(page.getOrder()));
	}
}
