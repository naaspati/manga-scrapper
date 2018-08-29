package mangafoxscrapper.scrapper;

import static sam.console.ANSI.cyan;
import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.console.VT100.erase_down;
import static sam.console.VT100.save_cursor;
import static sam.console.VT100.unsave_cursor;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.DoublePredicate;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import sam.config.MyConfig;
import sam.fileutils.FilesUtils;
import sam.internetutils.InternetUtils;
import sam.manga.newsamrock.converter.ConvertChapter;
import sam.manga.scrapper.ScrapperManga;
import sam.manga.scrapper.extras.Errors;
import sam.manga.scrapper.extras.FailedPage;
import sam.manga.scrapper.extras.Utils;
import sam.manga.scrapper.manga.parts.Chapter;
import sam.manga.scrapper.manga.parts.Manga;
import sam.manga.scrapper.manga.parts.Page;
import sam.manga.scrapper.scrappers.MangaScrapListener;
import sam.myutils.MyUtilsException;
import sam.myutils.MyUtilsSystem;
import sam.string.StringBuilder2;
import sam.string.StringUtils;
import sam.swing.SwingUtils;
import sam.tsv.Tsv;

public class Scraps {
	private final Scrapper scrapper;
	private int mangaProgress, chapterProgress, totalChaptersProgress; 
	private final int totalMangas;    
	private String nextChapterFormat, finalProgressFormat;
	private final int limit;
	private final InternetUtils internetUtils;
	private final List<FailedPage> failedPages = Collections.synchronizedList(new LinkedList<>());
	private final List<ConvertChapter> convertChapters = new ArrayList<>();

	// 1 -> manga.chaptersCount();
	private final String nextChapFormatBase = green("  (%%s/%s) ")+yellow("Chapter: ")+"%%s %%s";
	private final String totalProgessFormatBase = "\n\n\n"+yellow("chapter: ")+"%%s(%%s)/%s"+ cyan("  |  ")+ yellow("manga: ")+"%s/%s"+yellow("  |  failed pages: ")+"%%s";
	private final String aboutMangaFormat = new StringBuilder2()
			.green("(%s/%s)").ln()
			.repeat(' ', 5).yellow("id: ").append("%s")
			.yellow(",  name: ").append("%s").ln()
			.repeat(' ', 5).yellow("url: ").append("%s").ln()
			.toString();  
	private final String chapterCountFormat = StringUtils.repeat(" ", 5) + "chap_count: %s, missing_count: %s\n";
	private Iterable<Manga> mangas;

	public Scraps(Scrapper scrapper, Iterable<Manga> mangas, int limit, int mangasSize) {
		this.scrapper = scrapper;
		this.limit = limit;
		totalMangas = mangasSize;
		this.mangas = mangas;
		this.internetUtils = scrapper.internetUtils();
	}
	public void setMangas(Iterable<Manga> mangas) {
		this.mangas = mangas;
	}
	public void scrap() {
		scrap(Paths.get(MyConfig.MANGA_DIR));
	}
	public void scrap(final Path rootPath) {
		ExecutorService executorService = Executors.newFixedThreadPool(Optional.ofNullable(MyUtilsSystem.lookup("THREAD_COUNT")).map(Integer::parseInt).orElse(3));
		if(scrapper.dontCache == null || !(scrapper.dontCache  instanceof ConcurrentSkipListSet))
			scrapper.setDontCache(new ConcurrentSkipListSet<>());

		for (Manga manga : mangas) {
			chapterProgress = 0;
			mangaProgress++;

			if(manga.url == null || manga.dirName == null){
				System.out.println("\n"+red("SKIPPED:  url == null || dir == null"));
				continue;
			}

			System.out.printf(aboutMangaFormat, mangaProgress, totalMangas, manga.id, Utils.stringOf(manga.dirName), Utils.stringOf(manga.url));
			List<Chapter> chapters;

			try {
				ScrapperManga sm = scrapper.getManga(manga.url);
				chapters = sm.getChapters(mangalistener).map(c -> new Chapter(manga.id, c)).collect(Collectors.toList());
			} catch (Exception e1) {
				Errors.NO_CHAPTERS_SCRAPPED.addError(manga.id, null, null, manga.mangaName);
				continue;
			}
			manga.setChapters(chapters);

			if(chapters.isEmpty()){
				System.out.println(red("  no chapters extracted"));
				Errors.NO_CHAPTERS_SCRAPPED.addError(manga.id, null, null, manga.mangaName);
				continue;
			}   
			int ttl = chapters.size();
			if(manga.getFilter() != null) {
				DoublePredicate filter = manga.getFilter().getTester();
				chapters.removeIf(c -> !filter.test(c.getNumber()));
				chapters = chapters.size() <= limit ? chapters : chapters.stream().limit(limit).collect(Collectors.toList());
			}
			System.out.printf(chapterCountFormat, ttl, chapters.size());

			if(chapters.isEmpty()){
				System.out.println(red("no chapters to to proceed"));
				continue;
			}

			nextChapterFormat = format(nextChapFormatBase, chapters.size());
			finalProgressFormat = format(totalProgessFormatBase, chapters.size(), mangaProgress, totalMangas);

			chapterProgress = 0;

			totalProgress();
			final Path mangaDir = manga.dirPath(rootPath);
			Collections.reverse(chapters);

			for (Chapter chapter : chapters) {
				chapterProgress++;
				totalChaptersProgress++;
				System.out.printf(nextChapterFormat, chapterProgress, StringUtils.doubleToString(chapter.getNumber()), chapter.getTitle());
				totalProgress();
				save_cursor();
				System.out.print("  -> extracting pages");
				List<Page> pages;
				try {
					pages = chapter.loadPages();
				} catch (Exception e) {
					erase();
					System.out.println(red(" pages extracting failed: ")+e);
					Errors.CHAPTER.addError(manga.id, chapter.getNumber(), e, "scrapping pages failed: ", chapter.getTitle());
					continue;
				}
				erase();
				totalProgress();

				if(pages.isEmpty()) {
					System.out.println(red("  -> NO PAGES EXTRACTED"));
					continue;
				}

				System.out.print(cyan(" ("+pages.size()+") "));
				final Path chapterFolder = chapter.dirPath(mangaDir, manga);

				ConvertChapter cc = new ConvertChapter(chapter.mangaid, chapter.getNumber(), chapter.getTitle(), chapterFolder, chapterFolder);
				convertChapters.add(cc);

				String[] files = chapterFolder.toFile().list();
				if(files != null && files.length == pages.size()) {
					System.out.println(yellow(" COMPLETED"));
					continue;
				}

				try {
					Files.createDirectories(chapterFolder);
				} catch (IOException e) {
					System.out.println("\n"+red("Failed to create dir: ")+chapterFolder+"\t"+e+"\n");
					Errors.DOWNLOADER.addError(manga.id, chapter.getNumber(), e, "Failed to create dir: ", chapterFolder);
					continue;
				}
				try {
					executorService.invokeAll(pages.stream()
							.<Callable<Void>>map(page -> {
								Path target = page.getPath(chapterFolder);
								if(Files.exists(target)) {
									System.out.print(yellow(page.getOrder()+" "));
									return null;
								}
								return () -> {
									try {
										downloadPage(target, page);
										System.out.print(page.getOrder()+" ");
									} catch (Exception e) {
										System.out.print(red(page.getOrder()+" "));
										Errors.PAGE.addError(manga.id, chapter.getNumber(), e, page.getPageUrl(), target);
										failedPages.add(new FailedPage(target, page, chapter, manga, e));
									}
									return null;
								};
							})
							.filter(Objects::nonNull)
							.collect(Collectors.toList()));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.out.println();
			}
		}

		executorService.shutdown();
		try {
			executorService.awaitTermination(1000, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		retryFailedPages();
		finish();
	}
	private void retryFailedPages() {
		if(failedPages == null || failedPages.isEmpty())
			return;

		while(!failedPages.isEmpty() && JOptionPane.showConfirmDialog(null, "<html>try scrapping again?, <br>failed: "+failedPages.size()+" </html>") == JOptionPane.YES_OPTION) {
			failedPages.stream()
			.collect(Collectors.groupingBy(f -> f.manga))
			.forEach((manga, chapters) -> {
				System.out.print(cyan(manga.mangaName)+(chapters.size() == 1 ? " " : "\n"));
				chapters.stream()
				.collect(Collectors.groupingBy(f -> f.chapter))
				.forEach((chapter, pages) -> {
					System.out.print(yellow(chapter.getNumber()+" "+Utils.stringOf(chapter.getTitle()))+(chapters.size() == 1 ? " " : "\n"));

					for (FailedPage f : pages) {
						Page page = f.page;
						try {
							downloadPage(f.target, page);
							System.out.print(page.getOrder()+" ");
							failedPages.remove(f);
						} catch (Exception e) {
							System.out.print(red(page.getOrder()+" "));
						}
					}
					System.out.println();
				});
			});
		}
	}
	private void downloadPage(Path target, Page page) throws MalformedURLException, IOException, Exception {
		downloadPage(scrapper, internetUtils, target, page);
	}
	public static void downloadPage(Scrapper scrapper, InternetUtils internet_utils, Path target, Page page) throws MalformedURLException, IOException, Exception {
		scrapper.dontCache.add(page.getPageUrl());
		internet_utils.download(new URL(page.extractImageUrl(scrapper)), target);
		scrapper.dontCache.remove(page.getPageUrl());
	}
	public void finish() {
		saveFailedPages(failedPages);
		saveChapters(convertChapters);
	}
	public static void saveChapters(List<ConvertChapter> convertChapters) {
		Map<Path, List<Path>> grouped = convertChapters.stream().filter(Objects::nonNull).map(ConvertChapter::getTarget).collect(Collectors.groupingBy(p -> p.subpath(0,p.getNameCount() - 1))); 

		System.out.println();
		StringBuilder sb = new StringBuilder();
		grouped.forEach((s,t) -> {
			yellow(sb, s.getFileName()).append('\n');
			t.forEach(z -> sb.append("  ").append(z.getFileName()).append('\n'));
		});

		Tsv tsv = ConvertChapter.toTsv(convertChapters);
		try {
			Path path = Paths.get("chapters.tsv");
			if(Files.exists(path)) {
				Path p = FilesUtils.findPathNotExists(path);
				Files.move(path, p);
				System.out.println(path+"  moved to -> "+p);
			} 
			tsv.save(path);
			System.out.println(green("chapters.tsv created"));
		} catch (IOException e) {
			System.out.println(red("failed to save: chapters.tsv")+MyUtilsException.exceptionToString(e));
		}
		SwingUtils.copyToClipBoard(grouped.keySet().stream().map(Path::toString).collect(Collectors.joining("\n")));
	}
	public static void saveFailedPages(List<FailedPage> failedPages) {
		Path failedPagesPath = Paths.get("failed-pages.tsv");

		if(!failedPages.isEmpty()) {
			Tsv tsv = new Tsv("path", "url", "page url", "error");

			for (FailedPage fp : failedPages)
				tsv.addRow(fp.target.toString(), fp.page.getPageUrl(), fp.page.getImageUrl(), MyUtilsException.exceptionToString(fp.error));

			tsv.addRow("");
			tsv.addRow("");

			StringBuilder sbb = new StringBuilder();

			failedPages.stream()
			.collect(Collectors.groupingBy(fp -> fp.manga.id, TreeMap::new,  Collectors.mapping(fp -> fp.chapter.getNumber(), Collectors.toCollection(TreeSet::new))))
			.forEach((manga_id, chaps_nums) -> {
				sbb.append(manga_id).append(' ');
				chaps_nums.forEach(c -> sbb.append(StringUtils.doubleToString(c)).append(' '));
				sbb.append(' ');
			});

			tsv.addRow(sbb.toString());

			try {
				tsv.save(failedPagesPath);
				System.out.println(green(failedPagesPath + "  created"));
				System.out.println(red("failed manga-chaps: ")+sbb);
			} catch (IOException e) {
				System.out.println(red("failed to write: ")+failedPagesPath+"  error:"+e);
			}
		}
		else 
			delete(failedPagesPath);
	}
	private static void delete(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			System.out.println(red("deleting failed: ")+path +"  error:"+e);
		}
	}
	public void erase() {
		unsave_cursor();
		erase_down();
	}
	public void totalProgress() {
		save_cursor();
		System.out.printf(finalProgressFormat, chapterProgress, totalChaptersProgress, failedPages.size());
		unsave_cursor();
	}
	MangaScrapListener mangalistener = new MangaScrapListener() {
		@Override
		public void badChapterNumber(String number, String volume, String url, RuntimeException e) {
			System.out.println("\nbad number: "+number);
		}
	};

	private final StringBuilder sb = new StringBuilder();
	private final Formatter  formatter = new Formatter(sb);
	String format(String format, Object...args) {
		sb.setLength(0);
		formatter.format(format, args);
		return sb.toString();
	}

}