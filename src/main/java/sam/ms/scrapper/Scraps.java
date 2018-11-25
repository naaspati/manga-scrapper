package sam.ms.scrapper;

import static java.util.Collections.emptyList;
import static sam.console.ANSI.cyan;
import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.console.VT100.erase_down;
import static sam.console.VT100.save_cursor;
import static sam.console.VT100.unsave_cursor;
import static sam.downloader.db.entities.meta.DStatus.FAILED;
import static sam.downloader.db.entities.meta.DStatus.SKIPPED;
import static sam.downloader.db.entities.meta.DStatus.SUCCESS;
import static sam.myutils.Checker.isEmpty;

import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoublePredicate;
import java.util.stream.Stream;

import sam.console.ANSI;
import sam.downloader.db.entities.meta.DStatus;
import sam.downloader.db.entities.meta.ErrorSetter;
import sam.downloader.db.entities.meta.IDChapter;
import sam.downloader.db.entities.meta.IDManga;
import sam.downloader.db.entities.meta.IDPage;
import sam.internetutils.InternetUtils;
import sam.io.serilizers.StringWriter2;
import sam.manga.scrapper.ScrappedManga;
import sam.manga.scrapper.ScrapperListener;
import sam.manga.scrapper.SimpleScrapperListener;
import sam.ms.entities.Chapter;
import sam.ms.entities.Manga;
import sam.ms.entities.Page;
import sam.ms.extras.Utils;
import sam.myutils.System2;
import sam.string.StringBuilder2;
import sam.string.StringUtils;
import sam.tsv.Tsv;

public class Scraps implements Runnable {
	private final Scrapper scrapper;
	private int mangaProgress, chapterProgress, totalChaptersProgress; 
	private int mangaFailed, chapterFailed;
	private String nextChapterFormat, finalProgressFormat;
	private AtomicInteger failedPagesCount = new AtomicInteger(0);

	// 1 -> manga.chaptersCount();
	private final String nextChapFormatBase = green("  (%%s/%s) ")+yellow("Chapter: ")+"%%s %%s";
	private final String totalProgessFormatBase = "\n\n\n"+yellow("chapter: ")+"%%s/%s t:%%s"+ cyan("  |  ")+ yellow("manga: ")+"%s/%s"+yellow("  |  failed: (M: ")+"%%s, "+yellow("C: ")+"%%s, "+yellow("P: ")+"%%s"+yellow(" )");
	private final String aboutMangaFormat = new StringBuilder2()
			.green("(%s/%s)").ln()
			.repeat(' ', 5).yellow("id: ").append("%s")
			.yellow(",  name: ").append("%s").ln()
			.repeat(' ', 5).yellow("url: ").append("%s").ln()
			.toString();  

	private final String chapterCountFormat = StringUtils.repeat(" ", 5) + "chap_count: %s, missing_count: %s\n";
	private ScrapsListener listener;

	public Scraps(Scrapper scrapper, ScrapsListener listener) {
		this.scrapper = scrapper;
		this.listener = listener;
	}
	private final AtomicBoolean running = new AtomicBoolean();
	private InternetUtils internetUtils;
	private int THREAD_COUNT;
	private int totalMangas;

	@Override
	public void run() {
		if(running.getAndSet(true))
			throw new IllegalStateException("already running");

		internetUtils = new InternetUtils(true);
		internetUtils.SHOW_WARNINGS = false;
		internetUtils.SHOW_DOWNLOAD_WARNINGS = false;
		this.THREAD_COUNT = Optional.ofNullable(System2.lookup("THREAD_COUNT")).map(Integer::parseInt).orElse(3);
		totalMangas = listener.totalCountOfManga();
		System.out.println(ANSI.yellow("THREAD_COUNT: ")+THREAD_COUNT);

		try {
			start2(listener2());
		} catch (InterruptedException e) {
			System.err.println("STOPPING PROCESSS");
			e.printStackTrace();
			return;
		}
		retryFailedPages();
	}

	private void start2(ScrapsListener2 listener) throws InterruptedException {
		Path saveRoot = listener.getSaveRootPath();
		ExecutorService executorService = listener.executorService();

		while(true) {
			IDManga manga = Objects.requireNonNull(listener.nextManga());
			if(manga == ScrapsListener.STOP_MANGA) 
				break;

			int totalM = listener.totalCountOfManga();

			List<IDChapter> chapters = listener.getChapters(manga);
			if(chapters.isEmpty()) continue;

			nextChapterFormat = format(nextChapFormatBase, chapters.size());
			finalProgressFormat = format(totalProgessFormatBase, chapters.size(), mangaProgress, totalM);

			chapterProgress = 0;

			totalProgress();
			final Path mangaDir = listener.getMangaDir(manga, saveRoot);

			for (IDChapter chapter : chapters) {
				chapterProgress++;
				totalChaptersProgress++;
				System.out.printf(nextChapterFormat, chapterProgress, StringUtils.doubleToString(chapter.getNumber()), chapter.getTitle());
				totalProgress();

				Path chapterFolder = listener.getChapterFolder(manga, mangaDir, chapter);
				List<IDPage> pages = listener.getPages(manga, chapter, chapterFolder);
				if(pages.isEmpty()) continue;

				CountDownLatch latch = new CountDownLatch(pages.size());
				for (IDPage page : pages) 
					executorService.execute(new DownloadTask(chapterFolder, page, latch));

				latch.await();

				chapterFolder.toFile().delete();
				if(pages.stream().allMatch(p -> p.getStatus() == SUCCESS))
					setSuccess(chapter);
				else
					setFailed(chapters, "NOT ALL PAGES DOWNLOADED");
				erase();
				System.out.println();
			}
		}
		executorService.shutdown();
		executorService.awaitTermination(5000, TimeUnit.HOURS);

		save_cursor();
		erase();
		totalProgress();
	}

	private void setFailed(Object c, String error) {
		if(c instanceof ErrorSetter)
			((ErrorSetter)c).setError(error, DStatus.FAILED);
	}

	private void setSuccess(Object c) {
		if(c instanceof ErrorSetter)
			((ErrorSetter)c).setSuccess();

	}

	private class DownloadTask implements Runnable {
		private final IDPage page;
		private final CountDownLatch latch;
		private final Path chapterFolder;

		public DownloadTask(Path chapterFolder, IDPage page, CountDownLatch latch) {
			this.page = page;
			this.latch = latch;
			this.chapterFolder = chapterFolder;
		}

		@Override
		public void run() {
			String order =  String.valueOf(page.getOrder());
			String corder = order.concat(" ");
			Path target = chapterFolder.resolve(order);

			try {
				downloadPage(target, page);
				System.out.print(corder);
				setSuccess(page);
			} catch (Exception e) {
				System.out.print(red(corder));
				if(page instanceof ErrorSetter)
					((ErrorSetter)page).setFailed(null, e);
				if(page instanceof Page)
					((Page)page).setTarget(target);
				failedPagesCount.incrementAndGet();
			} finally {
				latch.countDown();
			}
		}
	}

	private void retryFailedPages() {
		throw new IllegalAccessError("not yes coded");
		/** FIXME
		 * 		if(failedPages == null || failedPages.isEmpty())
			return;

		while(!failedPages.isEmpty() && JOptionPane.showConfirmDialog(null, "<html>try scrapping again?, <br>failed: "+failedPages.size()+" </html>") == JOptionPane.YES_OPTION) {
			failedPages.stream()
			.collect(Collectors.groupingBy(f -> f.getChapter().getManga()))
			.forEach((manga, chapters) -> {
				System.out.print(cyan(manga.getMangaName())+(chapters.size() == 1 ? " " : "\n"));
				chapters.stream()
				.collect(Collectors.groupingBy(f -> f.chapter))
				.forEach((chapter, pages) -> {
					System.out.print(yellow(chapter.getNumber()+" "+Utils.stringOf(chapter.getTitle()))+": ");

					for (FailedPage f : pages) {
						Page page = (Page) f.page;
						try {
							downloadPage(f.target, page);
							System.out.print(page.getOrder()+" ");
							failedPages.remove(f);
							page.setSuccess();
						} catch (Exception e) {
							System.out.print(red(page.getOrder()+" "));
						}
					}
					System.out.println();
				});
			});
		}
		 */
	}
	private void downloadPage(Path target, IDPage page) throws MalformedURLException, IOException, Exception {
		downloadPage(scrapper, internetUtils, target, page);
	}
	public static void downloadPage(Scrapper scrapper, InternetUtils internet_utils, Path target, IDPage page) throws MalformedURLException, IOException, Exception {
		String s = scrapper.getPageImageUrl(page.getPageUrl());
		if(page instanceof Page)
			((Page)page).setImgUrl(s);
		internet_utils.download(new URL(s), target);
	}

	public static void saveFailedPages(Iterable<Manga> mangas) {
		Path failedPagesPath = Paths.get("failed-pages.tsv");
		
		Map<Integer, Set<Double>> command = new HashMap<>();
		List<Page> failedPages = new ArrayList<>();
		for (Manga manga : mangas) {
			for (IDChapter chap : manga) {
				for (IDPage page : chap) {
					if(page.getStatus() != SUCCESS) {
						failedPages.add((Page) page);
						command.computeIfAbsent(manga.getMangaId(), i -> new HashSet<>()).add(chap.getNumber());
					}
				}
			}
		}

		if(!failedPages.isEmpty()) {
			Tsv tsv = new Tsv("path", "url", "page url", "status", "error");

			for (Page fp : failedPages)
				tsv.addRow(fp.getTarget().toString(), fp.getPageUrl(), fp.getImgUrl(), fp.getStatus() == null ? null : fp.getStatus().toString(), fp.getError());
			
			StringBuilder sb = new StringBuilder();
			command.forEach((s,t) -> {
				sb.append(s).append(' ');
				t.forEach(d -> sb.append(StringUtils.doubleToString(d)).append(' '));
				sb.append(' ');
			});

			try {
				tsv.save(failedPagesPath);
				System.out.println(green(failedPagesPath + "  created"));
				System.out.println(red("failed manga-chaps: ")+sb);
				StringWriter2.setText(failedPagesPath.resolveSibling("command.txt"), sb);
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
	private void erase() {
		unsave_cursor();
		erase_down();
	}
	private void totalProgress() {
		save_cursor();
		System.out.printf(finalProgressFormat, chapterProgress, totalChaptersProgress, mangaFailed, chapterFailed, failedPagesCount.get());
		unsave_cursor();
	}

	private final StringBuilder sb = new StringBuilder();
	private final Formatter  formatter = new Formatter(sb);
	private String format(String format, Object...args) {
		synchronized (sb) {
			sb.setLength(0);
			formatter.format(format, args);
			return sb.toString();	
		}
	}
	private class ChapterScrapListen implements ScrapperListener{
		private int chapCount = 0;
		private final Manga manga ; 

		public ChapterScrapListen(Manga manga) {
			this.manga = manga;
		}

		@Override
		public void onPageSuccess(String chapterUrl, int order, String pageUrl) { }

		@Override public void onChapterSuccess(double number, String volume, String title, String url) {
			IDChapter c = manga.findChapter(url);
			if(c == null)
				manga.addChapter(new Chapter(manga, number, title, url, volume));
			chapCount++;
		}
		@Override
		public void onChapterFailed(String msg, Throwable e, String number, String volume, String title,
				String url) {
			Chapter c = manga.addChapter(new Chapter(manga, -1, title, url, volume));
			c.setFailed(msg, e);
			System.out.println(red(msg));
		}
	}

	private ScrapsListener2 listener2() {
		return new ScrapsListener2() {

			@Override
			public int totalCountOfManga() {
				return listener.totalCountOfManga();
			}
			@Override
			public int remainingCountOfManga() {
				return listener.remainingCountOfManga();
			}
			@Override
			public IDManga nextManga() {
				IDManga m = listener.nextManga();

				if(m == STOP_MANGA)
					return STOP_MANGA;

				Manga manga = (Manga) m;

				chapterProgress = 0;
				mangaProgress++;

				if(manga.getUrl() == null || manga.getDirName() == null){
					mangaFailed++;
					manga.setError("SKIPPED:  url == null || dir == null", null, SKIPPED);
					System.out.println("\n"+red("FAILED Manga:  url == null || dir == null")+manga);
					return nextManga();
				}

				erase_down();
				System.out.printf(aboutMangaFormat, mangaProgress, totalMangas, manga.getMangaId(), Utils.stringOf(manga.getDirName()), Utils.stringOf(manga.getUrl()));

				return manga;
			}
			@Override
			public ExecutorService executorService() {
				return Executors.newFixedThreadPool(THREAD_COUNT);
			}
			@Override
			public List<IDChapter> getChapters(IDManga m) {
				Manga manga = (Manga) m;
				int chapCount = 0;
				try {
					ScrappedManga sm = scrapper.scrapManga(manga.getUrl());
					ChapterScrapListen scl = new ChapterScrapListen(manga); 
					sm.getChapters(scl);
					chapCount = scl.chapCount;
				} catch (Exception e1) {
					mangaFailed++;
					manga.setFailed("Chapter Scrapping Failed", e1);
					return emptyList();
				}
				if(chapCount == 0){
					System.out.println(red("  no chapters extracted"));
					manga.setError("NO_CHAPTERS_SCRAPPED", null, FAILED);
					return emptyList();
				}
				manga.setSuccess();

				int ttl = manga.size();
				List<IDChapter> chapters = new ArrayList<>();
				DoublePredicate filter = manga.getFilter() != null ? manga.getFilter().getTester() : (d -> true);

				int n = 0;
				for (IDChapter c : manga) {
					if(n < manga.getLimit() && filter.test(c.getNumber())) {
						chapters.add((Chapter)c);
						n++;
					} else if(c.getStatus() != FAILED) {
						((Chapter)c).setStatus(SKIPPED);
					}
				}

				System.out.printf(chapterCountFormat, ttl, chapters.size());

				if(chapters.isEmpty())
					System.out.println(red("no chapters to to proceed"));

				return chapters;
			}
			@Override
			public List<IDPage> getPages(IDManga m, IDChapter c, Path chapterFolder) {
				Chapter chapter = (Chapter) c;

				if(chapter.getStatus() == SUCCESS) {
					System.out.println(yellow(" COMPLETED"));
					return emptyList();
				}

				save_cursor();
				System.out.print("  -> extracting pages");

				try {
					scrapper.scrapPages(chapter.getUrl(), new SimpleScrapperListener() {
						public void onPageSuccess(String chapterUrl, int order, String pageUrl) {
							IDPage p = chapter.findPage(pageUrl);
							if(p == null)
								chapter.addPage(new Page(chapter, order, pageUrl));
						};
					});
				} catch (Exception e) {
					chapterFailed++;
					erase();
					System.out.println(red(" pages extracting failed: ")+e);
					chapter.setFailed(" pages extracting failed: ", e);
					return emptyList();
				}
				erase();
				totalProgress();

				List<IDPage> pages = chapter.getPages();

				if(isEmpty(pages)) {
					System.out.println(chapter.getUrl()+"  "+red("  -> NO PAGES EXTRACTED"));
					return emptyList();
				}

				System.out.print(cyan(" ("+pages.size()+") "));
				chapter.setSourceTarget(chapterFolder);

				String[] files = chapterFolder.toFile().list();
				if(files != null) {
					if(files.length == pages.size()) {
						System.out.println(yellow(" COMPLETED"));
						return emptyList();
					}
					int[] array = Stream.of(files).mapToInt(s -> {
						try {
							return Integer.parseInt(s);
						} catch (NumberFormatException e) {
							return Integer.MAX_VALUE;
						}
					}).filter(i -> i != Integer.MAX_VALUE)
							.toArray();

					Arrays.sort(array);

					List<IDPage> pages2 = new ArrayList<>();
					for (IDPage p : pages) {
						if(Arrays.binarySearch(array, p.getOrder()) < 0) {
							pages2.add(p);
						} else
							System.out.print(yellow(p.getOrder()+" "));
					}
					pages = pages2;
					if(pages.isEmpty())
						return emptyList();
				}

				if(files == null) {
					try {
						Files.createDirectories(chapterFolder);
					} catch (IOException e) {
						System.out.println("\n"+red("Failed to create dir: ")+chapterFolder+"\t"+e+"\n");
						chapter.setFailed("Failed to create dir: "+chapterFolder, e);
						return emptyList();
					}	
				}
				chapter.setSuccess();
				return pages;
			}
			@Override
			public Path getChapterFolder(IDManga manga, Path mangaDir, IDChapter chapter) {
				return ((Chapter)chapter).dirPath(mangaDir, (Manga)manga);
			}
			@Override
			public Path getMangaDir(IDManga manga, Path saveRoot) {
				return ((Manga)manga).dirPath(saveRoot);
			}
		};
	}



}