package sam.manga.scrapper.manga.parts;

import java.util.Arrays;
import java.util.function.DoublePredicate;
import java.util.stream.DoubleStream;

public class ChapterFilter {
    private DoublePredicate tester;
    private StringBuilder sb = new StringBuilder("[");
    
    public ChapterFilter(DoubleStream.Builder bld) {
        double[] array = bld.build().sorted().toArray();
        tester = d -> Arrays.binarySearch(array, d) < 0;
        sb.append("missings");
    }
    public ChapterFilter() { }
    public ChapterFilter(String s) {
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
}
