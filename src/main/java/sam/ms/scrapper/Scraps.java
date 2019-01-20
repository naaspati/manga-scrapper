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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
import java.util.function.Function;
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
import sam.manga.samrock.chapters.ChapterFilterUtils;
import sam.manga.scrapper.FailedChapter;
import sam.manga.scrapper.ScrappedChapter;
import sam.manga.scrapper.ScrappedManga;
import sam.manga.scrapper.ScrappedPage;
import sam.manga.scrapper.Scrapper;
import sam.manga.scrapper.ScrapperException;
import sam.ms.entities.Chapter;
import sam.ms.entities.Manga;
import sam.ms.entities.Page;
import sam.ms.extras.Utils;
import sam.myutils.Checker;
import sam.myutils.System2;
import sam.reference.WeakQueue;
import sam.string.StringUtils;
import sam.tsv.Tsv;

public class Scraps implements Runnable {

	private final Scrapper scrapper;
	private List<IDPage> failedPages = Collections.synchronizedList(new ArrayList<>(100));
	private List<IDChapter> failedChapters = Collections.synchronizedList(new ArrayList<>(100));
	private final static WeakQueue<InternetUtils> internetUtils = new WeakQueue<>(true, InternetUtils::new);
	private ScrapsListener listener;
	private ProgressPrint out; 

	public Scraps(Scrapper scrapper, ScrapsListener listener) {
		this.scrapper = scrapper;
		this.listener = listener;
	}
	private final AtomicBoolean running = new AtomicBoolean();
	private int THREAD_COUNT;

	@Override
	public void run() {
		if(running.getAndSet(true))
			throw new IllegalStateException("already running");

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
		if(!System2.lookupBoolean("SKIP_RETRY", false))
			retryFailedPages(lis);
		saveFailed(lis);
	}
	private void start2(ScrapsListener2 listener) throws InterruptedException {
		ExecutorService executorService = Utils.dryRun() ? null : listener.executorService();

		out = new ProgressPrint();
		out.setMangaCount(listener.totalCountOfManga());

		while(true) {
			IDManga manga = Objects.requireNonNull(listener.nextManga());
			if(manga == ScrapsListener.STOP_MANGA) 
				break;

			List<IDChapter> chapters = listener.getChapters(manga);
			if(Checker.isEmpty(chapters)) continue;

			for (IDChapter chapter : chapters) {
				out.nextChapter(chapter);
				List<IDPage> pages = listener.getPages(manga, chapter);
				if(Checker.isEmpty(pages)) continue;

				if(Utils.dryRun()){
					for (IDPage p : pages)
						out.pageSuccess(p);
					continue;
				}

				int size = pages.size();
				CountDownLatch latch = new CountDownLatch(size);
				Collections.sort(pages, Comparator.comparingInt(IDPage::getOrder));

				for (int i = 0; i < size; i++) {
					IDPage page = pages.get(i);

					if(page.getImgUrl() == null) {
						try{
							setImageUrl(scrapper, i, pages);	
						} catch (ScrapperException| IOException e) {
							out.pageFailed(page);
							failedPages.add(page);
							setError(page, e);
						}
					}

					if(page.getImgUrl() == null) {
						latch.countDown();
						continue;
					}
					executorService.execute(() -> {
						Path target = listener.getSavePath(page);
						
						try {
							downloadPage(target, page);
							out.pageSuccess(page);
						} catch (IOException e) {
							out.pageFailed(page);
							failedPages.add(page);
						} finally {
							latch.countDown();
						}
					});
				}

				latch.await();

				listener.chapterPath(chapter).toFile().delete();
				if(pages.stream().allMatch(p -> p.getStatus() == SUCCESS))
					setSuccess(chapter);
				else {
					setFailed(chapter, "NOT ALL PAGES DOWNLOADED");
					failedChapters.add(chapter);
				}

				out.newLine();
			}

			out.newLine();
		}
		executorService.shutdown();
		executorService.awaitTermination(5000, TimeUnit.HOURS);

		if(out != null)
			out.close();
	}
	
	// ..\Scrapper\src\test\java\MangaTest.java#setImageUrl
	public static void setImageUrl(Scrapper scrapper, int index, List<IDPage> pages) throws ScrapperException, IOException {
		IDPage page = pages.get(index);

		String[] st = scrapper.getPageImageUrl(page.getPageUrl());
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
	
	private void setFailed(Object c, String error) {
		if(c instanceof ErrorSetter)
			((ErrorSetter)c).setError(error, DStatus.FAILED);
	}
	private void setSuccess(Object c) {
		if(c instanceof ErrorSetter)
			((ErrorSetter)c).setSuccess();
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

					pages.forEach(p -> p.setImgUrl(null));

					for (IDPage page : pages) {
						try {
							failed++;
							downloadPage(listener.getSavePath(page), page);
							System.out.print(page.getOrder()+" ");
							failedPages.remove(page);
							success.put(page, null);
						} catch (IOException e) {
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
	public void setError(Object page, Exception e) {
		if(page instanceof ErrorSetter)
			((ErrorSetter)page).setFailed(null, e);
	}
	private void downloadPage(Path target, IDPage page) throws IOException {
		InternetUtils internet = internetUtils.poll();
		try {
			downloadPage(page.getImgUrl(), internet, target);
			setSuccess(page);
		} catch (IOException e) {
			setError(page, e);
			throw e;
		} finally {
			internetUtils.offer(internet);
		}
	}
	public static void downloadPage(String url, InternetUtils internet_utils, Path target) throws MalformedURLException, IOException {
		Files.move(internet_utils.download(url), target, StandardCopyOption.REPLACE_EXISTING);
	}
	private void saveFailed(ScrapsListener2 listener) {
		Path failedPagesPath = Paths.get("failed-pages.tsv");

		if(failedPages.isEmpty()) 
			delete(failedPagesPath);
		else {
			Tsv tsv = new Tsv("path", "url", "page url", "status", "error");

			for (IDPage fp : failedPages) 
				tsv.addRow(listener.getSavePath(fp).toString(), fp.getPageUrl(), fp.getImgUrl(), fp.getStatus() == null ? null : fp.getStatus().toString(), fp.getError());

			StringBuilder sb = new StringBuilder();
			failedPages.stream().filter(p -> p instanceof Page)
			.map(p -> (Page)p)
			.collect(Collectors.groupingBy(p -> p.getChapter().getManga(), IdentityHashMap::new, Collectors.toMap(p -> p.getChapter(), p -> Boolean.TRUE, (o, n) -> o, IdentityHashMap::new)))
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
				System.out.println(yellow("created: ")+failedPagesPath);
			} catch (IOException e) {
				System.out.println(red("failed to write: ")+failedPagesPath+"  error:"+e);
			}
		}

		Path failedChapterPath = Paths.get("failed-chapters.tsv");

		if(failedChapters.isEmpty())
			delete(failedChapterPath);
		else {
			StringBuilder sb = new StringBuilder();
			Function<IDChapter, String> failedPages = ch -> {
				if(ch == null)
					return "";

				sb.setLength(0);
				for (IDPage p : ch) {
					if(p.getStatus() != DStatus.SUCCESS)
						sb.append(p.getOrder()).append(' ');
				}
				return sb.toString();
			};

			Tsv tsv = new Tsv("number", "title", "url", "source","pages", "error");
			for (IDChapter c : failedChapters) 
				tsv.addRow(String.valueOf(c.getNumber()), c.getTitle(), c.getUrl(), c.getSource() == null ? "" : c.getSource().toString(), failedPages.apply(c), c.getError());

			try {
				tsv.save(failedChapterPath);
				System.out.println(yellow("created: ")+failedChapterPath);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
	private static void delete(Path path) {
		try {
			Utils.deleteIfExists(path);
		} catch (IOException e) {
			System.out.println(red("deleting failed: ")+path +"  error:"+e);
		}
	}

	private static final boolean ONE_PAGE_CHECK = System2.lookupBoolean("ONE_PAGE_CHECK", true);

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
				return THREAD_COUNT == 1 ? Executors.newSingleThreadExecutor() : Executors.newFixedThreadPool(THREAD_COUNT);
			}
			@Override
			public List<IDChapter> getChapters(IDManga m) {
				Manga manga = (Manga) m;
				int chapCount;
				try {
					ScrappedManga sm = scrapper.scrapManga(manga.getUrl());
					ScrappedChapter[] chaps = sm.getChapters();

					int count = 0;
					for (ScrappedChapter sc : chaps) {
						if(sc instanceof FailedChapter) {
							FailedChapter f = (FailedChapter) sc;
							Chapter c = manga.addChapter(new Chapter(manga, -1, sc.getTitle(), sc.getUrl(), sc.getVolume()));
							String s = f.toString();
							c.setFailed(s, f.getException());
							out.chapterFailed(s, f.getException());
							failedChapters.add(c);
						} else {
							IDChapter c = manga.findChapter(sc.getUrl());
							if(c == null) {
								c = new Chapter(manga, sc.getNumber(), sc.getTitle(), sc.getUrl(), sc.getVolume());
								manga.addChapter(c);
							}
							if(Utils.DEBUG)
								System.out.println("chapter scrapped: "+c.getNumber()+"  "+c.getTitle());
							count++;
						}
					}
					chapCount = count;
				} catch (IOException | ScrapperException e1) {
					out.mangaFailed("Chapter Scrapping Failed", e1, manga);
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
				DoublePredicate filter = manga.getFilter() == null ? ChapterFilterUtils.ALL_ACCEPT_FILTER : manga.getFilter();

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

				/** FIXME somewhere status is set SUCCESS regardless of actual value
				 * if(chapter.getStatus() == SUCCESS) {
					out.chapterCompleted(1);
					return emptyList();
				}
				 */

				out.temporary("  -> extracting pages");

				try {
					ScrappedPage[] pages = scrapper.scrapPages(chapter.getUrl());
					for (ScrappedPage sp : pages) {
						IDPage p = chapter.findPage(sp.getPageUrl());
						if(p == null)
							chapter.addPage(p = new Page(chapter, sp.getOrder(), sp.getPageUrl()));

						if(sp.getImgUrl() != null)
							((Page)p).setImgUrl(sp.getImgUrl());
					}
				} catch (Exception e) {
					out.undoTemporary();
					out.chapterFailed(" pages extracting failed: ", e);
					chapter.setFailed(" pages extracting failed: ", e);
					failedChapters.add(chapter);
					return emptyList();
				}

				out.undoTemporary();

				List<IDPage> pages = chapter.getPages();

				if(isEmpty(pages)) {
					out.chapterFailed(chapter.getUrl()+"  -> NO PAGES EXTRACTED", null);
					chapter.setFailed("NO PAGES EXTRACTED", null);
					failedChapters.add(chapter);
					return emptyList();
				}

				out.pageCount(pages.size());

				if(ONE_PAGE_CHECK && pages.size() == 1) {
					out.chapterFailed(chapter.getUrl()+"  -> ONE PAGE", null);
					chapter.setFailed("ONE PAGE", null);
					failedChapters.add(chapter);
					return emptyList();
				}

				Path chapterFolder = chapter.getPath();
				chapter.setSourceTarget(chapterFolder);

				String[] files = chapterFolder.toFile().list();
				if(files != null) {
					if(files.length >= pages.size()) {
						out.chapterCompleted(2);
						return emptyList();
					}
					int[] array = Stream.of(files).mapToInt(s -> {
						try {
							return Integer.parseInt(s);
						} catch (NumberFormatException e) {
							e.printStackTrace();
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
						Utils.createDirectories(chapterFolder);
					} catch (IOException e) {
						out.chapterFailed("Failed to create dir: "+chapterFolder, e);
						chapter.setFailed("Failed to create dir: "+chapterFolder, e);
						failedChapters.add(chapter);
						return emptyList();
					}	
				}
				chapter.setSuccess();
				return pages;
			}
		};
	}
}