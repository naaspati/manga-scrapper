package sam.ms;

import static sam.console.ANSI.FINISHED_BANNER;
import static sam.console.ANSI.red;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import sam.swing.SwingClipboard;

public class Downloader {
	public void tsv(List<String> argsList) throws Exception {
        int sizeIndex = argsList.indexOf("size");
        int limit = -1;

        if(sizeIndex >= 0){
            if(sizeIndex >= argsList.size() - 1){
                System.out.println("no value set for argument : "+red("size"));
                return;
            }
            try {
                limit = Integer.parseInt(argsList.get(sizeIndex + 1));
            } catch (NumberFormatException e) {
                System.out.println("failed to parse values: "+red(argsList.get(sizeIndex + 1))+" for option "+red("size"));
                return;
            }
        }
        System.out.println("size: "+limit);
        new TsvScrapper(mangas(), limit < 0 ? Integer.MAX_VALUE : limit)
        .call();
	}

	private MangaList mangas() throws SQLException {
		return MangaList.createInstance();
	}

	public void mchap(List<String> args) throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException, SQLException {
		if(args.isEmpty())
            System.out.println(red("invalid count of commands: ")+args.toString());
        else{
        	SwingClipboard.setString(String.join(" ", args));
            new MangaIdChapterNumberScrapper(args, mangas()).start();
            System.out.println(FINISHED_BANNER);
        }
	}
    
    /* FIXME use it or delete it
     *     private void notifyme() {
        boolean errorOccured = Stream.of(Errors.values()).anyMatch(e -> e.getErrors() != null);

        try {
            if(errorOccured){
                System.out.println(red(createUnColoredBanner("ERRORS")));

                StringBuilder sbb = new StringBuilder();
                Errors.failedMangaIdChapterNumberMap.forEach((mangaid, chapNums) -> {
                    sbb.append(mangaid).append(' ');
                    chapNums.stream()
                    .filter(Objects::nonNull)
                    .mapToDouble(d -> d)
                    .forEach(d -> (d == (int)d ? sbb.append((int)d) : sbb.append(d)).append(' '));
                });
                sbb.append("\n\n");

                Errors.failedMangaIdChapterNumberMap.keySet()
                .stream().map(mangasMap::get)
                .forEach(m -> {
                    if(m == null)
                        return;
                    sbb.append(m.id).append(Errors.separator)
                    .append(m.dirName).append(Errors.separator)
                    .append(m.url).append('\n');
                });

                sbb.append("\n\n");

                String data = Stream.of(Errors.values())
                        .filter(e -> e.getErrors() != null)
                        .reduce(sbb, (sb,error) -> sb.append("\n--------------------------\n").append(error).append('\n').append(error.getErrors()).append('\n'), StringBuilder::append).toString();

                Files.write(Paths.get("errors.txt"), data.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            else
                Files.deleteIfExists(Paths.get("errors.txt"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println(FINISHED_BANNER);

        MyUtilsCmd.beep(5);

        if(errorOccured)
            FileOpenerNE.openFile(new File("."));
    }
     */



}
