package sam.ms.extras;


public enum DStatus {
	FAILED, SUCCESS, UNKNOWN, SKIPPED;

	public static DStatus status(String s) {
		return s == null ? DStatus.UNKNOWN : DStatus.valueOf(s);
	}
}
