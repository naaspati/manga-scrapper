package sam.ms.scrapper;

import static java.lang.System.arraycopy;
import static java.lang.System.out;
import static sam.console.ANSI.cyan;
import static sam.console.ANSI.red;
import static sam.console.ANSI.yellow;
import static sam.console.VT100.erase_down;
import static sam.console.VT100.save_cursor;
import static sam.console.VT100.unsave_cursor;

import java.util.Arrays;
import java.util.function.UnaryOperator;

import sam.console.ANSI;
import sam.downloader.db.entities.meta.IDChapter;
import sam.downloader.db.entities.meta.IDPage;
import sam.ms.entities.Manga;
import sam.ms.extras.Utils;
import sam.myutils.Checker;
import sam.string.StringBuilder2;

class ProgressPrint {
	private final StringBuilder2 builder = new StringBuilder2();

	public void setMangaCount(int mangaCount) {
		total.mangaCount(mangaCount);
	}
	private boolean closed = false;
	public void close() {
		if(closed) return ;
		closed = true;

		save_cursor();
		erase();
		out.print(total.charArray());
		out.println();
	}

	private TotalProgress total = new TotalProgress();
	private final char[] PROGRESS = ANSI.yellow("\nprogress: ").toCharArray();
	private final char[] ID = ANSI.yellow("id: ").toCharArray();
	private final char[] NAME = ANSI.yellow(",  name: ").toCharArray();
	private final char[] URL = ANSI.yellow("url: ").toCharArray();

	public void nextManga(Manga manga) {
		total.nextManga();
		erase_down();

		if(manga.getUrl() == null || manga.getDirName() == null){
			total.mangaFailed();
			out.println("\n"+red("FAILED Manga:  url == null || dir == null")+manga);
		} else {

			/**
			 private final BasicFormat aboutMangaFormat = new BasicFormat(new StringBuilder2()
			.green("({}/{})").ln()
			.append(indent).yellow("id: ").append("{}")
			.yellow(",  name: ").append("{}").ln()
			.append(indent).yellow("url: ").append("{}").ln()
			.toString());  

			 */

			/**
			 * example
			 * progress: 1/1
			 * id: 615625,  name: Kyou Mei Machi
			 * url: http://www.mangahere.cc/manga/kyou_mei_machi
			 * 
			 */
			synchronized (builder) {
				builder.setLength(0);
				builder.append(PROGRESS).cyan(Integer.toString(total.mangaProgress)).append('/').append(toChars(total.mangaCount)).ln()
				.append(ID).append(Integer.toString(manga.getMangaId())).append(NAME).append(manga.getMangaName()).ln()
				.append(URL).append(manga.getUrl()).ln();
				out.print(builder.toString());
				builder.setLength(0);
			}
		} 


		totalProgress();
	}

	private final char[] MANGA_FAILED_MSG = red("Chapter Scrapping Failed").toCharArray();

	public void mangaFailed(String string, Exception e1, Object cause) {
		total.mangaFailed();

		if(Utils.DEBUG) {
			out.println(MANGA_FAILED_MSG);
			out.println(cause);
			e1.printStackTrace(out);
		} else {
			out.print(MANGA_FAILED_MSG);
			out.println(exception(e1));
		}
		totalProgress();
	}

	private final char[] INDENT = "  ".toCharArray();
	private final char[] SEPERATOR = ANSI.cyan(" | ").toCharArray();
	
	// private final BasicFormat nextChapFormat = new BasicFormat(green("  ({}/{}) ")+yellow("Chapter: ")+"{} {}");
	
	public void nextChapter(IDChapter c) {
		
		synchronized (builder) {
			total.chapterProgress();
			builder.append(INDENT).cyan(Integer.toString(total.chapterProgress)).append(SEPERATOR)
			.append(dToString(c.getNumber())).append(' ');
			String s = c.getTitle();
			if(!Checker.isEmpty(s))
				builder.append(s).append(' ');
			
			out.print(builder.toString());
			
			builder.setLength(0);
			totalProgress();
		}
	}

	private char[] dToString(double number) {
		int n = (int)number;
		if(n == number)
			return toChars(n);
		
		return Double.toString(number).toCharArray();
	}

	private void erase() {
		unsave_cursor();
		erase_down();
	}
	private void totalProgress() {
		save_cursor();
		out.print(total.charArray());
		unsave_cursor();
	}

	static final char[] ZERO = {'0'}; 

	static final int CHAPTER_PROGRESS = 1;
	static final int CHAPTER_COUNT = 3;
	static final int TOTAL_CHAPTERS_PROGRESS = 5;
	static final int MANGA_PROGRESS = 7;
	static final int MANGA_COUNT = 9;
	static final int MANGA_FAILED = 11;
	static final int CHAPTER_FAILED = 14;
	static final int PAGE_FAILED = 17;

	private class TotalProgress {
		int chapterCount;
		int mangaCount;
		int failedPagesCount;

		int mangaProgress, chapterProgress, totalChaptersProgress; 
		int mangaFailed, chapterFailed;
		boolean changed = false;

		public void nextManga() {
			chapterProgress = 0;
			chapterCount = 0;
			mangaProgress++;

			changed = true;
			array[CHAPTER_PROGRESS] = ZERO;
			array[CHAPTER_COUNT] = ZERO;
			array[MANGA_PROGRESS] = toChars(mangaProgress);
		}

		private char[][] array = {
				("\n\n\n"+yellow("chapter: ")).toCharArray(), // 0
				ZERO,   // 1
				{'/'}, // 2
				ZERO,   // 3,
				{' ','t', ':'}, // 4
				ZERO,   // 5,
				(cyan("  |  ")+ yellow("manga: ")).toCharArray(), // 6
				ZERO,   // 7
				{'/'}, // 8
				ZERO,   // 9,
				yellow("  |  failed: (M: ").toCharArray(), // 10
				ZERO,   // 11,
				{',', ' ' }, // 12
				yellow("C: ").toCharArray(), //13
				ZERO,   // 14,
				{',', ' ' }, // 15
				yellow("P: ").toCharArray(), //16
				ZERO,   // 17,
				yellow(" )").toCharArray()
		};

		char[] result = new char[0];

		public char[] charArray() {
			if(!changed ) return result;
			changed = false;
			int size = 0;

			for (char[] c : array) 
				size += c.length;

			if(size != result.length)
				result = new char[size];

			int index = 0;
			for (char[] c : array) {
				arraycopy(c, 0, result, index, c.length);
				index += c.length; 
			} 
			return result;
		}
		public void chapterProgress() {
			changed = true;
			chapterProgress++;
			array[CHAPTER_PROGRESS] = toChars(chapterProgress);
		}
		public void mangaCount(int mangaCount) {
			changed = true;
			this.mangaCount = mangaCount;
			array[MANGA_COUNT] = toChars(mangaCount);
		}
		public void mangaFailed() {
			changed = true;
			mangaFailed++;
			array[MANGA_FAILED] = toChars(mangaFailed);
		}
		public void chapCount(int current) {
			changed = true;
			this.chapterCount = current;
			totalChaptersProgress += current;
			array[CHAPTER_COUNT] = toChars(chapterCount);
			array[TOTAL_CHAPTERS_PROGRESS] = toChars(totalChaptersProgress);
		}
		public void chapterFailed() {
			changed = true;
			chapterFailed++;
			array[CHAPTER_FAILED] = toChars(chapterFailed);
		}
		public void failedPagesCount() {
			changed = true;
			failedPagesCount++;
			array[PAGE_FAILED] = toChars(failedPagesCount);
		}
	}

	private final char[] CHAP_COUNT = ANSI.yellow("chap_count: ").toCharArray();
	private final char[] MISSING_COUNT = ANSI.yellow(",  missing_count: ").toCharArray();
	private final char[] CHAPTERS = ANSI.yellow(" chapters: ").toCharArray();

	// private final BasicFormat chapterCountFormat = new BasicFormat(indent + ANSI.yellow("chap_count:")+" {}, "+ANSI.yellow("missing_count:")+" {}\n");

	public void chapterCount(int total, int remaining) {
		this.total.chapCount(remaining);
		// chap_count: 23, missing_count: 16
		synchronized (builder) {
			builder.append(CHAP_COUNT).append(toChars(total)).append(MISSING_COUNT).append(toChars(remaining)).ln();
			if(remaining != 0)
				builder.append(CHAPTERS).ln();

			out.print(builder.toString());	
			builder.setLength(0);
		}
	}

	public void temporary(String string) {
		save_cursor();
		erase();
		out.print(string);
	}
	public void undoTemporary() {
		erase();
		totalProgress();
	}
	public void chapterFailed(String string, Throwable e) {
		total.chapterFailed();
		if(e == null)
			out.println(red(string));
		else
			out.println(red(string)+exception(e));
		totalProgress();
	}

	private String exception(Throwable e) {
		if(e == null)
			return "";
		String msg = e.getMessage();
		if(Checker.isEmpty(e.getMessage()))
			return e.getClass().getSimpleName().concat("()");
		if(msg.length() > 20)
			return e.getClass().getSimpleName()+"("+msg.substring(0, 17)+"...)";
		else
			return e.getClass().getSimpleName()+"("+msg+")";
	}

	public void newLine() {
		out.println();
	}

	private static class Wrap {
		private final char[] c1, c2, c3, prefix, suffix;
		private final int prefixLen;
		private final int minGap;

		public Wrap(UnaryOperator<String> wrapper, int minGap) {
			String place = "--place--";
			String s = wrapper.apply(place);

			if(s == place) {
				this.prefix = new char[0];
				this.suffix = new char[0];				
			} else {
				int n = s.indexOf(place);
				prefix = new char[n];
				s.getChars(0, n, prefix, 0);

				suffix = new char[s.length() - n - place.length()];
				s.getChars(n+place.length(), s.length(), suffix, 0);
			}
			prefixLen = prefix.length;
			this.minGap = minGap;

			c1 = array(minGap);
			c2 = array(minGap+1);
			c3 = array(minGap+2);
		}

		@SuppressWarnings("unused")
		public Wrap(char[] prefix, char[] suffix, int minGap) {
			this.prefix = prefix;
			this.suffix = suffix;
			prefixLen = prefix.length;
			this.minGap = minGap;

			c1 = array(minGap);
			c2 = array(minGap+1);
			c3 = array(minGap+2);
		}
		private char[] array(int addSize) {
			char[] c1 = new char[prefix.length + suffix.length + addSize];
			arraycopy(prefix, 0, c1, 0, prefix.length);
			arraycopy(suffix, 0, c1, c1.length - suffix.length, suffix.length);
			return c1;
		}
		public char[] wrap(char[] c) {
			int ln = c.length;
			if(ln == minGap){
				c1[prefixLen] = c[0];
				return c1;
			}
			if(ln == minGap+1){
				c2[prefixLen] = c[0];
				c2[prefixLen+1] = c[1];
				return c2;
			}
			if(ln == minGap+2) {
				c3[prefixLen] = c[0];
				c3[prefixLen+1] = c[1];
				c3[prefixLen+2] = c[2];
				return c3;
			}
			char[] chars = array(c.length);
			arraycopy(c, 0, chars, prefix.length, c.length);
			return chars;
		}	
	}


	private final Wrap redNumber = new Wrap(ANSI::red, 2);

	public void pageFailed(IDPage page) {
		synchronized(numbers) {
			out.print(redNumber.wrap(numberAndSpace(page.getOrder())));
			total.failedPagesCount();
			totalProgress();	
		}
	}
	private final Wrap yelloNumber = new Wrap(ANSI::yellow, 2);
	public void pageSkipped(IDPage page) {
		synchronized(numbers) {
			out.print(yelloNumber.wrap(numberAndSpace(page.getOrder())));
			total.failedPagesCount();
			totalProgress();	
		}
	}
	public void pageSuccess(IDPage page) {
		synchronized(numbers) {
			out.print(numberAndSpace(page.getOrder()));	
		}
	}

	private final char[] n1 = {' ',' '}, n2 = {' ',' ',' '}, n3 = {' ',' ',' ',' '};
	private char[] numberAndSpace(final int n) {
		if(n < 10) {
			n1[0] = toChar(n); 
			return n1;
		}

		char[] c = toChars(n);
		switch (c.length) {
			case 2:
				n2[0] = c[0];
				n2[1] = c[1];
				return n2;
			case 3:
				n3[0] = c[0];
				n3[1] = c[1];
				n3[2] = c[2];
				return n3;
			default:
				c = Arrays.copyOf(c, c.length+1);
				c[c.length - 1] = ' ';
				return c;
		}
	}
	private char toChar(final int n) {
		return (char) ('0' + n);
	}

	private final char[][] numbers = new char[100][];
	{
		for (char i = '0'; i <= '9'; i++) 
			numbers[i] = new char[]{i};
	}

	private char[] toChars(final int n) {
		char[] c = n >= numbers.length ? null : numbers[n];
		if(c != null) 
			return c;

		c = toString(n).toCharArray();

		if(n < numbers.length) 
			numbers[n] =  c;	

		return c;
	}
	private String[] integerToString = new String[100];

	private String toString(int n) {
		if(n >= integerToString.length)
			return Integer.toString(n);

		String s = integerToString[n];
		if(s == null)
			return integerToString[n] = Integer.toString(n);
		return s;
	}
	private final char[] COMPLETED  = ANSI.yellow(" COMPLETED_ ").toCharArray();
	private final int compltedIndex; 
	{
		int n = 0;
		for (int i = 0; i < COMPLETED.length; i++) {
			if(COMPLETED[i] == 'D' && COMPLETED[i+1] == '_') {
				n = i+2;
				break;
			}
		}
		compltedIndex = n;
	}
	public void chapterCompleted(int n) {
		if(n > 9)
			throw new IllegalArgumentException(String.valueOf(n));
		COMPLETED[compltedIndex] = (char) ('0'+n);
		out.print(COMPLETED);
		save_cursor();
		erase();
		out.println();
	}
	private final Wrap cyanWrap = new Wrap(s -> ANSI.cyan(" ("+s+") "), 1);
	public void pageCount(int size) {
		out.print(cyanWrap.wrap(toChars(size)));
	}
	public static String stringOf(String s) {
		return s == null ? red("null") : s;
	}


}
