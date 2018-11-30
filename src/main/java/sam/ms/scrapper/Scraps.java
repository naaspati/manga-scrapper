package sam.ms.scrapper;

import static java.util.Collections.emptyList;
import static sam.console.ANSI.cyan;
import static sam.console.ANSI.green;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.downloader.db.entities.meta.DStatus.FAILED;
import static sam.downloader.db.entities.meta.DStatus.SKIPPED;
import static sam.downloader.db.entities.meta.DStatus.SUCCESS;
import static sam.myutils.Checker.isEmpty;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoublePredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JOptionPane;

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
import sam.myutils.Checker;
import sam.myutils.System2;
import sam.string.StringUtils;
import sam.tsv.Tsv;

public class Scraps implements Runnable {
	private final Scrapper scrapper;
	private List<IDPage> failedPages = Collections.synchronizedList(new ArrayList<>(100));
	private ScrapsListener listener;
	private ProgressPrint out; 

	public Scraps(Scrapper scrapper, ScrapsListener listener) {
		this.scrapper = scrapper;
		this.listener = listener;
	}
	private final AtomicBoolean running = new AtomicBoolean();
	private InternetUtils internetUtils;
	private int THREAD_COUNT;

	@Override
	public void run() {
		if(running.getAndSet(true))
			throw new IllegalStateException("already running");

		internetUtils = new InternetUtils(true);
		internetUtils.SHOW_WARNINGS = false;
		internetUtils.SHOW_DOWNLOAD_WARNINGS = false;
		this.THREAD_COUNT = Optional.ofNullable(System2.lookup("THREAD_COUNT")).map(Integer::parseInt).orElse(3);
		System.out.println(ANSI.yellow("\n\nTHREAD_COUNT: ")+THREAD_COUNT);

		ScrapsListener2 lis = listener2(); 
		try {
			start2(lis);
		} catch (InterruptedException e) {
			System.err.println("STOPPING PROCESSS");
			e.printStackTrace();
			return;
		}
		retryFailedPages(lis);
	}
	private void start2(ScrapsListener2 listener) throws InterruptedException {
		ExecutorService executorService = listener.executorService();
		
		out = new ProgressPrint();
		out.setMangaCount(listener.totalCountOfManga());

		while(true) {
			IDManga manga = Objects.requireNonNull(listener.nextManga());
			if(manga == ScrapsListener.STOP_MANGA) 
				break;

			List<IDChapter> chapters = listener.getChapters(manga);
			if(chapters.isEmpty()) continue;

			for (IDChapter chapter : chapters) {
				out.nextChapter(chapter);
				List<IDPage> pages = listener.getPages(manga, chapter);
				if(pages.isEmpty()) continue;

				CountDownLatch latch = new CountDownLatch(pages.size());
				for (IDPage page : pages) 
					executorService.execute(new DownloadTask(page, latch, listener.getSavePath(page)));

				latch.await();

				listener.chapterPath(chapter).toFile().delete();
				if(pages.stream().allMatch(p -> p.getStatus() == SUCCESS))
					setSuccess(chapter);
				else
					setFailed(chapters, "NOT ALL PAGES DOWNLOADED");
				out.mangaFinish();
			}
		}
		executorService.shutdown();
		executorService.awaitTermination(5000, TimeUnit.HOURS);
		
		if(out != null)
			out.close();
	}

	private void setFailed(Object c, String error, Throwable e) {
		if(c instanceof ErrorSetter)
			((ErrorSetter)c).setError(error, e, DStatus.FAILED);
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
		private final Path savePath;

		public DownloadTask(IDPage page, CountDownLatch latch, Path savePath) {
			this.page = page;
			this.latch = latch;
			this.savePath = savePath;
		}

		@Override
		public void run() {
			try {
				downloadPage(savePath, page);
				out.pageSuccess(page);
				setSuccess(page);
			} catch (Exception e) {
				if(page instanceof ErrorSetter)
					((ErrorSetter)page).setFailed(null, e);
				out.pageFailed(page);
				failedPages.add(page);
			} finally {
				latch.countDown();
			}
		}
	}

	private void retryFailedPages(ScrapsListener2 listener) {
		if(Checker.isEmpty(failedPages))
			return;

		IdentityHashMap<IDPage, Void> success = new IdentityHashMap<>();
		System.out.println();

		while(!failedPages.isEmpty() && JOptionPane.showConfirmDialog(null, "<html>try scrapping again?, <br>failed: "+failedPages.size()+" </html>") == JOptionPane.YES_OPTION) {
			failedPages.stream()
			.collect(Collectors.groupingBy(f -> listener.getManga(listener.getChapter(f)), IdentityHashMap::new, Collectors.toList()))
			.forEach((manga, chapters) -> {

				System.out.print(cyan(manga.getMangaName())+(chapters.size() == 1 ? " " : "\n"));
				int[] totPageFailed = {0}; 

				chapters.stream()
				.collect(Collectors.groupingBy(f -> listener.getChapter(f), IdentityHashMap::new, Collectors.toList()))
				.forEach((chapter, pages) -> {
					System.out.print(yellow(chapter.getNumber()+" "+ProgressPrint.stringOf(chapter.getTitle()))+": ");
					int failed = 0;

					for (IDPage page : pages) {
						try {
							failed++;
							downloadPage(listener.getSavePath(page), page);
							System.out.print(page.getOrder()+" ");
							failedPages.remove(page);
							setSuccess(page);
							success.put(page, null);
						} catch (Exception e) {
							setFailed(page, null, e); 
							System.out.print(red(page.getOrder()+" "));
							failed++;
						}
					}
					if(failed == 0)
						setSuccess(chapter);
					else
						totPageFailed[0] += failed; 
					System.out.println();
				});
				if(totPageFailed[0] == 0)
					setSuccess(manga);
			});
			failedPages.removeIf(success::containsKey);
			success.clear();
		}
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

	public static void saveFailedPages(List<IDPage> failedPages, ScrapsListener listener) {
		Path failedPagesPath = Paths.get("failed-pages.tsv");

		if(!failedPages.isEmpty()) {
			Tsv tsv = new Tsv("path", "url", "page url", "status", "error");

			for (IDPage fp : failedPages) 
				tsv.addRow(listener.getSavePath(fp).toString(), fp.getPageUrl(), fp.getImgUrl(), fp.getStatus() == null ? null : fp.getStatus().toString(), fp.getError());

			StringBuilder sb = new StringBuilder();
			failedPages.stream().filter(p -> p instanceof Page)
			.map(p -> (Page)p)
			.collect(Collectors.groupingBy(p -> p.getChapter().getManga(), IdentityHashMap::new, Collectors.toMap(p -> p.getChapter(), p -> null, (o, n) -> o, IdentityHashMap::new)))
			.forEach((manga, chaps) -> {
				sb.append(manga.getMangaId()).append(' ');
				chaps.forEach((chap,NULL) -> sb.append(StringUtils.doubleToString(chap.getNumber())).append(' '));
				sb.append(' ');
			});

			try {
				tsv.save(failedPagesPath);
				System.out.println(green(failedPagesPath + "  created"));
				System.out.println(red("failed manga-chaps: ")+sb);
				if(sb.length() != 0)
					StringWriter2.setText(failedPagesPath.resolveSibling("failed-mangas-command.txt"), sb);
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
	private class ChapterScrapListen implements ScrapperListener{
		private int chapCount = 0;
		private final Manga manga ; 

		public ChapterScrapListen(Manga manga) {
			this.manga = manga;
		}

		@Override
		public void onPageSuccess(String chapterUrl, int order, String pageUrl) { }

		@Override 
		public void onChapterSuccess(double number, String volume, String title, String url) {
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
			out.chapterFailed(msg, e);
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

				if(m == STOP_MANGA) {
					out.close();
					return STOP_MANGA;
				}

				Manga manga = (Manga) m;
				out.nextManga(manga);

				if(manga.getUrl() == null || manga.getDirName() == null){
					manga.setError("SKIPPED:  url == null || dir == null", null, SKIPPED);
					return nextManga();
				}
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
					out.mangaFailed("Chapter Scrapping Failed", e1);
					manga.setFailed("Chapter Scrapping Failed", e1);
					return emptyList();
				}
				if(chapCount == 0){
					out.chapterFailed("  no chapters extracted", null);
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

				out.chapterCount(ttl, chapters.size());

				if(chapters.isEmpty())
					out.chapterFailed("no chapters to to proceed", null);

				return chapters;
			}
			@Override
			public List<IDPage> getPages(IDManga m, IDChapter c) {
				Chapter chapter = (Chapter) c;

				if(chapter.getStatus() == SUCCESS) {
					out.chapterCompleted();
					return emptyList();
				}
				out.temporary("  -> extracting pages");

				try {
					scrapper.scrapPages(chapter.getUrl(), new SimpleScrapperListener() {
						public void onPageSuccess(String chapterUrl, int order, String pageUrl) {
							IDPage p = chapter.findPage(pageUrl);
							if(p == null)
								chapter.addPage(new Page(chapter, order, pageUrl));
						};
					});
				} catch (Exception e) {
					out.undoTemporary();
					out.chapterFailed(" pages extracting failed: ", e);
					chapter.setFailed(" pages extracting failed: ", e);
					return emptyList();
				}
				
				out.undoTemporary();
				
				List<IDPage> pages = chapter.getPages();

				if(isEmpty(pages)) {
					out.chapterFailed(chapter.getUrl()+"  -> NO PAGES EXTRACTED", null);
					return emptyList();
				}

				out.pageCount(pages.size());
				Path chapterFolder = chapter.getPath();
				chapter.setSourceTarget(chapterFolder);

				String[] files = chapterFolder.toFile().list();
				if(files != null) {
					if(files.length == pages.size()) {
						out.chapterCompleted();
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
							out.pageSkipped(p);
					}
					pages = pages2;
					if(pages.isEmpty())
						return emptyList();
				}

				if(files == null) {
					try {
						Files.createDirectories(chapterFolder);
					} catch (IOException e) {
						out.chapterFailed("Failed to create dir: "+chapterFolder, e);
						chapter.setFailed("Failed to create dir: "+chapterFolder, e);
						return emptyList();
					}	
				}
				chapter.setSuccess();
				return pages;
			}
		};
	}



}