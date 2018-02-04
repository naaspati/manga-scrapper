package sam.manga.scrapper.extras;

public class Count{
    public int success = -1;
    public int total = 1;
    public boolean bothEqual() {
        return success == total;
    }
}