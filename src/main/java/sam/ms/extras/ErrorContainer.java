package sam.ms.extras;


import java.io.PrintWriter;
import java.util.Arrays;

import sam.api.store.entities.meta.SChapter;
import sam.api.store.entities.meta.SManga;
import sam.api.store.entities.meta.SPage;
import sam.reference.WeakAndLazy;
import sam.string.StringWriter2;

public interface ErrorContainer {
	static WeakAndLazy<StringWriter2> keep = new WeakAndLazy<>(StringWriter2::new);  
	
	default void setFailed(String msg, Throwable e) {
		setError(msg, e, DStatus.FAILED);
	}
	default void setSuccess() {
		setError(null, null, DStatus.SUCCESS);
	}
	default void setError(String msg, Throwable e, DStatus status) {
		if(msg == null && e == null)
			setError(null, status);
		else if(e == null)
			setError(msg, status);
		else {
			String s ;
			synchronized(keep) {
				StringWriter2 sw = keep.get();
				sw.clear();
				
				if(this instanceof SPage) {
					SPage p = (SPage) this;
					sw.append("page_url: ").append(p.getPageUrl()).append('\n');
					sw.append("img_url: ").append(p.getImgUrl()).append('\n');
				}
				if(this instanceof SChapter) {
					SChapter p = (SChapter) this;
					sw.append("url: ").append(p.getUrl()).append('\n');
				}
				if(this instanceof SManga) {
					SManga p = (SManga) this;
					sw.append("url: ").append(Arrays.toString(p.getUrls())).append('\n');
				}
				
				if(msg != null) 
					sw.append(msg).append('\n');
				
				e.printStackTrace(new PrintWriter(sw));
				s = sw.toString();
			}
			setError(s, status);
		}
	}
	void setError(String error, DStatus status);
	String getError();
}
