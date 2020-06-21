package sam.ms.scrapper;
import static java.nio.file.Files.move;
import static java.nio.file.Files.newBufferedWriter;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Arrays.binarySearch;
import static java.util.Arrays.sort;
import static java.util.Collections.emptyList;
import static java.util.Collections.sort;
import static java.util.Collections.synchronizedList;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static javax.swing.JOptionPane.YES_OPTION;
import static javax.swing.JOptionPane.showConfirmDialog;
import static sam.api.config.InjectorKeys.DRY_RUN;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.ms.extras.DStatus.FAILED;
import static sam.ms.extras.DStatus.SKIPPED;
import static sam.ms.extras.DStatus.SUCCESS;
import static sam.myutils.Checker.exists;
import static sam.myutils.Checker.isEmpty;
import static sam.myutils.MyUtilsPath.TEMP_DIR;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.DoublePredicate;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import com.google.gson.Gson;

import sam.api.FileUtils;
import sam.api.chapter.ChapterFilterBase;
import sam.api.config.ConfigService;
import sam.api.store.entities.meta.MutableList;
import sam.api.store.entities.meta.SChapter;
import sam.api.store.entities.meta.SManga;
import sam.api.store.entities.meta.SPage;
import sam.console.ANSI;
import sam.internetutils.InternetUtils;
import sam.io.fileutils.FilesUtilsIO;
import sam.io.serilizers.StringIOUtils;
import sam.manga.api.scrapper.ScrappedChapter;
import sam.manga.api.scrapper.ScrappedManga;
import sam.manga.api.scrapper.ScrappedPage;
import sam.manga.api.scrapper.ScrapperException;
import sam.manga.api.scrapper.Scrappers;
import sam.manga.api.scrapper.base.FailedChapter;
import sam.manga.samrock.chapters.MinimalChapter;
import sam.ms.entities.StatusContainer;
import sam.ms.extras.DStatus;
import sam.ms.extras.ErrorContainer;
import sam.myutils.Checker;
import sam.reference.WeakPool;
import sam.string.StringUtils;
public class Scraps  {
	private final List<SPage> failedPages = synchronizedList(new ArrayList<>(100));
	private final List<SChapter> failedChapters = synchronizedList(new ArrayList<>(100));
	private final static WeakPool<InternetUtils> internetUtils = new WeakPool<>(true, InternetUtils::new);
	private final AtomicBoolean running = new AtomicBoolean();
	private final boolean dryRun;
	private final Provider<Scrappers> scrappersProvider;
	private final Provider<ProgressPrint> progressPrintProvider;
	private final ConfigService config;
	private final boolean onePageCheck;
	private final IdentityHashMap<SChapter, ScrappedChapter> scrappedChapters = new IdentityHashMap<>();
	private final IdentityHashMap<SManga, ScrappedManga> scrappedMangas = new IdentityHashMap<>();
	
	private Path mydir_tempdir = TEMP_DIR.resolve(getClass().getSimpleName());
	private ScrapsFactory factory;
	private ProgressPrint out;
	
	private int threadCount;
	private final FileUtils fileUtils;
	private final PrintStream printout;

	@Inject
	public Scraps(FileUtils fileUtils, Provider<ProgressPrint> progressPrintProvider, Provider<Scrappers> scrappersProvider, ConfigService config, @Named(DRY_RUN) boolean dryRun, PrintStream out) {
		this.printout = out;
		this.scrappersProvider = scrappersProvider;
		this.config = config;
		this.dryRun = dryRun;
		this.fileUtils = fileUtils;
		this.progressPrintProvider = progressPrintProvider;

		if (exists(mydir_tempdir))
			FilesUtilsIO.delete(mydir_tempdir.toFile());

		onePageCheck = config.getBoolean("scraps.one.page.check", true);
	}

	public void start(ScrapsFactory factory, Iterable<SManga> mangas, int mangaSize) {
		if (!running.compareAndSet(false, true))
			throw new IllegalStateException("already running");
		
		this.factory = factory;
		
		try {
			this.threadCount = config.getInt("scraps.thread.count", 3);
			printout.println(ANSI.yellow("\n\nTHREAD_COUNT: ") + threadCount);

			try {
				start2(mangas, mangaSize);
			} catch (InterruptedException e) {
				printout.println("STOPPING PROCESSS");
				e.printStackTrace(printout);
				return;
			}
			if (!config.getBoolean("skip.retry", false))
				retryFailedPages();
			saveFailed();
		} finally {
			running.set(false);
		}
	}

	private void start2(Iterable<SManga> mangas, int mangaSize) throws InterruptedException {
		ExecutorService executorService = dryRun ? null : executorService();

		out = progressPrintProvider.get();
		out.setMangaCount(mangaSize);
		Scrappers scrappers = scrappersProvider.get();
		
		for (SManga manga : mangas) {
			Objects.requireNonNull(manga);
			out.nextManga(manga);

			if (isEmpty(manga.getUrls()) || manga.getDirName() == null) {
				((ErrorContainer) manga).setError("SKIPPED:  url == null || dir == null", null, SKIPPED);
				continue;
			}
			
			List<SChapter> chapters = getChapters(scrappers, manga);
			if (isEmpty(chapters))
				continue;
			
			Function<SChapter, Path> chapterPathResolver = factory.chapterPathResolver(manga);

			for (SChapter chapter : chapters) {
				out.nextChapter(chapter);
				
				if (dryRun) {
					out.newLine();
					continue;
				}
				
				Path chapterPath = chapterPathResolver.apply(chapter);
				List<SPage> pages = getPages(scrappers, manga, chapter, chapterPath);
				
				if (isEmpty(pages))
					continue;

				int size = pages.size();
				CountDownLatch latch = new CountDownLatch(size);
				sort(pages, comparingInt(SPage::getOrder));

				for (int i = 0; i < size; i++) {
					SPage page = pages.get(i);

					if (page.getImgUrl() == null) {
						try {
							setImageUrl(chapter, i, pages);
						} catch (ScrapperException | IOException e) {
							setError(page, e);
							pageFailed(page);
						}
					}

					if (page.getImgUrl() == null) {
						latch.countDown();
						continue;
					}
					executorService.execute(() -> {
						Path target = factory.pagePath(chapterPath, page);

						try {
							if (downloadPage(target, page))
								out.pageSuccess(page);
							else
								pageFailed(page);
						} finally {
							latch.countDown();
						}
					});
				}

				latch.await();

				chapterPath.toFile().delete();
				if (pages.stream().allMatch(p -> ((StatusContainer) p).getStatus() == SUCCESS))
					setSuccess(chapter);
				else {
					setFailed(chapter, "NOT ALL PAGES DOWNLOADED");
					failedChapters.add(chapter);
				}

				out.newLine();
			}

			out.newLine();
		}
		if(executorService != null) {
			executorService.shutdown();
			executorService.awaitTermination(5000, TimeUnit.HOURS);
		}

		if (out != null)
			out.close();
	}
	
	public void setImageUrl(SChapter chapter, int index, List<SPage> pages) throws ScrapperException, IOException {
		SPage page = pages.get(index);

		ScrappedChapter sc = getScrappedChapter(chapter);
		String[] st = sc == null ? null : sc.getPageImageUrl(page.getPageUrl());
		if(Checker.isEmpty(st))
			return;

		int order = page.getOrder();
		for (String s : st) {
			int n = index++;
			int o = order++;

			if(n >= pages.size())
				return;

			if(pages.get(n).getOrder() == o)
				pages.get(n).setImgUrl(s);
		}
	}

	private ScrappedChapter getScrappedChapter(SChapter chapter) {
		ScrappedChapter sc = scrappedChapters.get(chapter);
		// TODO what to do when not found
		return sc;
	}

	private final Map<SChapter, Path> chaps = new IdentityHashMap<>();
	private volatile Object[] current_chap = { null, null };

	private void pageFailed(SPage page) {
		out.pageFailed(page);
		failedPages.add(page);

		Path dir;
		Object[] o = current_chap;

		if (o[0] == factory.getChapter(page)) {
			dir = (Path) o[1];
		} else {
			synchronized (this) {
				dir = chaps.computeIfAbsent(factory.getChapter(page), c -> {
					Path d = mydir_tempdir.resolve(String.valueOf(factory.getManga(c).getMangaId()))
							.resolve(StringUtils.doubleToString(c.getNumber()));
					try {
						fileUtils.createDirectories(d);
					} catch (IOException e1) {
						e1.printStackTrace(printout);
						return null;
					}
					return d;
				});
			}
			current_chap = new Object[] { factory.getChapter(page), dir };
		}

		if (dir == null)
			return;

		if (((ErrorContainer) page).getError() != null) {
			try {
				StringIOUtils.write(((ErrorContainer) page).getError(), dir.resolve(out.toString(page.getOrder())));
			} catch (IOException e) {
				e.printStackTrace(printout);
			}
		}
	}

	private void setFailed(Object c, String error) {
		if (c instanceof ErrorContainer)
			((ErrorContainer) c).setError(error, DStatus.FAILED);
	}

	private void setSuccess(Object c) {
		if (c instanceof ErrorContainer)
			((ErrorContainer) c).setSuccess();
	}

	private void retryFailedPages() {
		if (isEmpty(failedPages))
			return;

		printout.println();

		while (!failedPages.isEmpty() && showConfirmDialog(null,
				"<html>try scrapping again?, <br>failed: " + failedPages.size() + " </html>") == YES_OPTION) {

			Map<SManga, Map<SChapter, List<SPage>>> map = failedPages.stream()
					.collect(groupingBy(p -> factory.getManga(factory.getChapter(p)), IdentityHashMap::new,
							groupingBy(factory::getChapter, IdentityHashMap::new, toList())));

			map.forEach((manga, chapters) -> {
				printout.println(ANSI.yellow(manga.getId()) + " : " + manga.getMangaName());
				Function<SChapter, Path> chapterPathResolver = factory.chapterPathResolver(manga);
				
				chapters.forEach((chapter, pages) -> {
					printout.print(ANSI.yellow(chapter.getNumber()) + " : ");
					Path chapterPath = chapterPathResolver.apply(chapter);
					int failed = 0;

					for (SPage page : pages) {
						failed++;

						if (downloadPage(factory.pagePath(chapterPath, page), page)) {
							printout.print(page.getOrder() + " ");
							failedPages.remove(page);
						} else {
							printout.print(red(page.getOrder() + " "));
							failed++;
						}
					}
					if (failed == 0)
						setSuccess(chapter);
					printout.println();
				});
			});
		}
	}

	public void setError(Object page, Exception e) {
		if (page instanceof ErrorContainer)
			((ErrorContainer) page).setFailed(null, e);
	}

	private boolean downloadPage(Path target, SPage page) {
		InternetUtils internet = internetUtils.poll();

		try {
			downloadPage(page.getImgUrl(), internet, target);
			setSuccess(page);
			return true;
		} catch (IOException e) {
			setError(page, e);
			return false;
		} finally {
			internetUtils.offer(internet);
		}
	}

	public static void downloadPage(String url, InternetUtils internet_utils, Path target)
			throws MalformedURLException, IOException {
		move(internet_utils.download(url), target, REPLACE_EXISTING);
	}

	private void saveFailed() {
		Path failedPagesPath = Paths.get("failed-pages.tsv");

		if (failedPages.isEmpty())
			delete(failedPagesPath);
		else {
			Object map = failedPages.stream().filter(p -> factory.getChapter(p) != null)
					.collect(groupingBy(p -> factory.getManga(factory.getChapter(p)).getId(), HashMap::new,
							groupingBy(p -> factory.getChapter(p).getUrl())));

			writeJson(map, failedPagesPath);
		}

		Path failedChapterPath = Paths.get("failed-chapters.json");

		if (failedChapters.isEmpty())
			delete(failedChapterPath);
		else
			writeJson(failedChapters, failedChapterPath);
	}

	private void writeJson(Object object, Path path) {
		try (BufferedWriter w = newBufferedWriter(path, CREATE, TRUNCATE_EXISTING)) {
			new Gson().toJson(object, w);
			printout.println(yellow("created: ") + path);
		} catch (IOException e) {
			e.printStackTrace(printout);
		}
	}

	private void delete(Path path) {
		try {
			fileUtils.deleteIfExists(path);
		} catch (IOException e) {
			printout.println(red("deleting failed: ") + path + "  error:" + e);
		}
	}

	public ExecutorService executorService() {
		return threadCount == 1 ? Executors.newSingleThreadExecutor() : Executors.newFixedThreadPool(threadCount);
	}

	public List<SChapter> getChapters(Scrappers scrappers, SManga m) {
		for (String url : m.getUrls()) {
			List<SChapter> chaps = url == null ? null : getChapters(scrappers, m, url);
			if (chaps != null)
				return chaps;
		}
		return null;
	}

	public int scrapChapters(SManga manga, ScrappedManga sm, BiConsumer<FailedChapter, SChapter> onFailed)
			throws IOException, ScrapperException {
		ScrappedChapter[] chaps = sm.getChapters();
		if (isEmpty(chaps))
			return -1;

		int count = 0;
		SChapter c;
		@SuppressWarnings("unchecked")
		MutableList<SChapter> chapters = (MutableList<SChapter>) manga.getChapters();

		for (ScrappedChapter sc : chaps) {
			if (sc instanceof FailedChapter) {
				FailedChapter f = (FailedChapter) sc;
				c = chapters.add(factory.newChapter(manga, sc));
				onFailed.accept(f, c);
				((ErrorContainer) c).setFailed(f.toString(), f.getException());
			} else {
				c = chapters.find(sc.getUrl());
				if (c == null) {
					c = factory.newChapter(manga, sc);
					chapters.add(c);
				}
				count++;
			}
			this.scrappedChapters.put(c, sc);
		}
		return count;
	}

	private List<SChapter> getChapters(Scrappers scrappers, SManga manga, String manga_url) {
		int chapCount;
		out.mangaUrl(manga_url);
		ErrorContainer errorContainer = (ErrorContainer) manga;

		try {
			ScrappedManga sm = scrappers.apply(manga, manga_url);
			this.scrappedMangas.put(manga, sm);
			chapCount = scrapChapters(manga, sm, (f, c) -> {
				String s = f.toString();
				out.chapterFailed(s, f.getException());
				failedChapters.add(c);
			});
		} catch (Exception e1) {
			out.mangaFailed("Chapter Scrapping Failed", e1, manga);
			errorContainer.setFailed("Chapter Scrapping Failed", e1);
			return emptyList();
		}

		if (chapCount == 0) {
			out.chapterFailed("  no chapters extracted", null);
			errorContainer.setError("NO_CHAPTERS_SCRAPPED", null, FAILED);
			return emptyList();
		}
		errorContainer.setSuccess();

		int ttl = manga.size();
		List<SChapter> chapters = new ArrayList<>();
		DoublePredicate filter = factory.filterFor(manga);
		if (filter == null)
			filter = ChapterFilterBase.ALL_ACCEPT_FILTER;

		int n = 0;
		int limit = factory.limitFor(manga);
		for (MinimalChapter c : manga) {
			if (n < limit && filter.test(c.getNumber())) {
				chapters.add((SChapter) c);
				n++;
			} else if (getStatus(c) != FAILED) {
				setStatus(c, SKIPPED);
			}
		}

		out.chapterCount(ttl, chapters.size());

		if (chapters.isEmpty())
			out.chapterFailed("no chapters to to proceed", null);

		return chapters;
	}

	private void setStatus(Object c, DStatus s) {
		((StatusContainer) c).setStatus(s);
	}

	private DStatus getStatus(Object c) {
		return c == null ? null : ((StatusContainer) c).getStatus();
	}

	public List<SPage> getPages(Scrappers scrappers, SManga manga, SChapter chapter, Path chapterFolder) {
		out.temporary("  -> extracting pages");

		ErrorContainer chapError = (ErrorContainer) chapter;
		@SuppressWarnings("unchecked")
		MutableList<SPage> pages = (MutableList<SPage>) chapter.getPages();

		try {
			ScrappedPage[] array = this.scrappedChapters.get(chapter).getPages();

			for (ScrappedPage sp : array) {
				SPage p = pages.find(sp.getPageUrl());
				if (p == null)
					pages.add(p = factory.newPage(chapter, sp));

				if (sp.getImgUrl() != null)
					p.setImgUrl(sp.getImgUrl());
			}
		} catch (Exception e) {
			out.undoTemporary();
			out.chapterFailed(" pages extracting failed: ", e);
			chapError.setFailed(" pages extracting failed: ", e);
			failedChapters.add(chapter);
			return emptyList();
		}

		out.undoTemporary();

		if (pages.isEmpty()) {
			out.chapterFailed(chapter.getUrl() + "  -> NO PAGES EXTRACTED", null);
			chapError.setFailed("NO PAGES EXTRACTED", null);
			failedChapters.add(chapter);
			return emptyList();
		}

		out.pageCount(pages.size());

		if (onePageCheck && pages.size() == 1) {
			out.chapterFailed(chapter.getUrl() + "  -> ONE PAGE", null);
			chapError.setFailed("ONE PAGE", null);
			failedChapters.add(chapter);
			return emptyList();
		}

		String[] files = chapterFolder.toFile().list();
		List<SPage> pages2 = null;

		if (files != null) {
			if (files.length >= pages.size()) {
				out.chapterCompleted(2);
				return emptyList();
			}
			int[] array = Stream.of(files).mapToInt(s -> {
				try {
					int n = s.lastIndexOf('.');
					return Integer.parseInt(n > 0 ? s.substring(0, n) : s);
				} catch (NumberFormatException e) {
					e.printStackTrace(printout);
					return Integer.MAX_VALUE;
				}
			}).filter(i -> i != Integer.MAX_VALUE).toArray();

			sort(array);

			pages2 = new ArrayList<>();
			for (SPage p : pages) {
				if (binarySearch(array, p.getOrder()) < 0) {
					pages2.add(p);
				} else
					out.pageSkipped(p);
			}
			if (pages.isEmpty())
				return emptyList();
		}

		if (files == null) {
			try {
				fileUtils.createDirectories(chapterFolder);
			} catch (IOException e) {
				out.chapterFailed("Failed to create dir: " + chapterFolder, e);
				chapError.setFailed("Failed to create dir: " + chapterFolder, e);
				failedChapters.add(chapter);
				return emptyList();
			}
		}
		chapError.setSuccess();

		return pages2 != null ? pages2 : new ArrayList<>(pages.asList());
	}
}