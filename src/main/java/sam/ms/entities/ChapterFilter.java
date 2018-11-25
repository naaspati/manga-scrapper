package sam.ms.entities;

import java.util.Arrays;
import java.util.function.DoublePredicate;

import sam.console.ANSI;
import sam.ms.extras.Utils;
import sam.string.StringUtils;

public class ChapterFilter {
	public final int manga_id;
    private DoublePredicate tester;
    private StringBuilder sb = new StringBuilder("[");
    
    public ChapterFilter(int manga_id, double[] array) {
    	this.manga_id = manga_id;
        Arrays.sort(array);
        tester = d -> Arrays.binarySearch(array, d) < 0;
        if(Utils.isPrintFilter()) {
            sb.append("NOT IN -> ");
            String separator = ANSI.yellow(", ");
            for (double d : array) 
                sb.append(StringUtils.doubleToString(d)).append(separator);
        } else 
            sb.append("MISSINGS");
    }
    public ChapterFilter(int manga_id) {
    	this.manga_id = manga_id;
    }
    public ChapterFilter(int manga_id, String s) {
    	this.manga_id = manga_id;
        add(s);
    }
    public DoublePredicate add(String s) {
        DoublePredicate dp = make(s);
        
        if(sb.length() != 1)
            sb.append(", ");
        sb.append(s);
        
        return tester = tester == null ? dp : tester.or(dp);
    }
    public DoublePredicate getTester() {
        return tester;
    }
    public boolean hasTester() {
        return tester != null;
    }
    
    private DoublePredicate make(String s) {
        final int index1 = s.indexOf('-');
        final int index2 = s.indexOf('_');

        if(index1 < 0 && index2 < 0){
            return d -> d == parse(s);
        }

        if(index1 == 0 || index2 == 0){
            return  index1 == 0 
                    ? d -> d <= parse(s.substring(1)) 
                    : d -> d < parse(s.substring(1));
        }
        else if(index1 == s.length() - 1 || index2 == s.length() - 1){
            return index1 == s.length() - 1  
                    ? d -> d >= parse(s.substring(0, s.length() - 1)) 
                    : d -> d > parse(s.substring(0, s.length() - 1));
        }
        else{
            double n1 = parse(s.substring(0, index1 > 0 ? index1 : index2));
            double n2 = parse(s.substring((index1 > 0 ? index1 : index2) + 1, s.length()));

            return index1 > 0 
                    ? d -> n1 <= d && d <= n2
                    : d -> n1 < d && d < n2;
        }
    }
    private double parse(String s) {
        return Double.parseDouble(s);
    }
    
    @Override
    public String toString() {
        int length = sb.length();
        sb.append(']');
        String s = sb.toString();
        sb.setLength(length);
        return s;
    }
	@Override
	public int hashCode() {
		return manga_id;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		ChapterFilter other = (ChapterFilter) obj;
		return manga_id == other.manga_id;
	}
    
}
