package sam.manga.scrapper.extras;

import java.nio.file.Path;

import sam.manga.scrapper.manga.parts.Chapter;
import sam.manga.scrapper.manga.parts.Manga;
import sam.manga.scrapper.manga.parts.Page;

public  class FailedPage {
    public final Path target;
    public final Page page;
    public final Chapter chapter;
    public final Manga manga;
    public final Exception error;

    public FailedPage(Path target, Page page, Chapter chapter, Manga manga, Exception error) {
        this.target = target;
        this.page = page;
        this.chapter = chapter;
        this.manga = manga;
        this.error = error;
    }
}