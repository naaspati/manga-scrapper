package sam.manga.scrapper.manga.parts;
import java.io.Serializable;

public class Page implements Serializable {
    private static final long serialVersionUID = 4851601945753922142L;

    public  final int order;
    public final String pageUrl;
    public final String imageUrl;

    public Page(int order, String pageUrl, String imageUrl) {
        this.order = order;
        this.pageUrl = pageUrl;
        this.imageUrl = imageUrl;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Page [order=")
        .append(order).append(", pageUrl=").append(pageUrl).append(", imageUrl=").append(imageUrl).append("]");
        return builder.toString();
    }
}