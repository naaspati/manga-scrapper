package sam.ms;

import java.util.function.DoublePredicate;

import sam.api.chapter.ChapterFilterBase;

class Filter2 extends ChapterFilterBase {
	DoublePredicate predicate;

	public Filter2(int manga_id) {
		super(manga_id);
	}

	public void add(String s) {
		check();

		DoublePredicate dp = make(s);
		predicate = predicate == null ? dp : predicate.or(dp);
	}

	@Override
	public boolean test(double value) {
		if (!complete)
			throw new IllegalStateException("not completed");

		return predicate.test(value);
	}

	private DoublePredicate make(String s) {
		int index1 = s.indexOf('-');
		int index2 = s.indexOf('_');

		if (index1 < 0 && index2 < 0) {
			double r = parse(s);
			return (d -> d == r);
		}

		if (index1 == 0 || index2 == 0) {
			double r = parse(s.substring(1));

			return index1 == 0 ? (d -> d <= r) : (d -> d < r);
		} else if (index1 == s.length() - 1 || index2 == s.length() - 1) {
			double r = parse(s.substring(0, s.length() - 1));

			return index1 == s.length() - 1 ? (d -> d >= r) : (d -> d > r);
		} else {
			double n1 = parse(s.substring(0, index1 > 0 ? index1 : index2));
			double n2 = parse(s.substring((index1 > 0 ? index1 : index2) + 1, s.length()));

			return index1 > 0 ? (d -> n1 <= d && d <= n2) : (d -> n1 < d && d < n2);
		}
	}

	private double parse(String s) {
		return Double.parseDouble(s);
	}
}
