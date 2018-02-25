package sam.manga.scrapper.extras;

import static sam.manga.newsamrock.mangas.MangasMeta.MANGA_ID;
import static sam.manga.newsamrock.mangas.MangasMeta.MANGA_NAME;
import static sam.manga.newsamrock.mangas.MangasMeta.getMangaId;
import static sam.manga.newsamrock.mangas.MangasMeta.getMangaName;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TableView.TableViewSelectionModel;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import sam.fx.clipboard.FxClipboard;
import sam.fx.helpers.FxHelpers;
import sam.fx.popup.FxPopupShop;
import sam.manga.newsamrock.SamrockDB;
import sam.manga.newsamrock.chapters.Chapter;
import sam.manga.scrapper.extras.IdNameView.TempManga;

public class IdNameView extends Application implements EventHandler<KeyEvent>, ChangeListener<TempManga> {
    private List<TempManga> allData = new ArrayList<>();
    private ObservableList<TempManga> selectedItems;
    private SamrockDB samrock;

    private final Label chapterLabel = new Label();
    private TableView<TempManga> table;
    private TableViewSelectionModel<TempManga> model;
    private final TextField searchTF = new TextField();

    class TempManga {
        final String name;
        final String lowerCaseName;
        final int id;
        final String idString;
        Chapter lastChap;
        public TempManga(ResultSet rs) throws SQLException {
            this.name = getMangaName(rs);
            this.id = getMangaId(rs);
            this.idString = String.valueOf(id);
            lowerCaseName = name.toLowerCase();
        }

        SimpleStringProperty nameP;
        public ObservableValue<String> nameProperty() {
            return nameP != null ? nameP : (nameP = new SimpleStringProperty(name));
        }
        SimpleStringProperty idP;
        public SimpleStringProperty idProperty() {
            return idP != null ? idP : (idP = new SimpleStringProperty(idString));
        }
    }
    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("Name list");
        FxPopupShop.setParent(stage);

        samrock = new SamrockDB();
        samrock.manga().selectAll(rs -> allData.add(new TempManga(rs)), MANGA_ID, MANGA_NAME);

        table = getTable();

        BorderPane root = new BorderPane(table, null, getSelectedListView(), getBottomPane(), null);

        stage.setScene(new Scene(root));
        stage.getScene().getStylesheets().add("styles.css");
        stage.show();
        Platform.runLater(searchTF::requestFocus);
    }
    
    private void copy() {
        TempManga item = model.getSelectedItem();
        if(item == null)
            return;
        
        model.clearSelection();
        String s = item.idString;
        FxPopupShop.showHidePopup(s, 1000);
        FxClipboard.copyToClipboard(s);
        selectedItems.add(item);
        table.getItems().remove(item);
        allData.remove(item);
    }

    private Node getBottomPane() {
        searchTF.setOnKeyReleased(this);

        VBox v = new VBox(5,chapterLabel, searchTF);
        v.setPadding(new Insets(5));

        return v;
    }
    
    @Override
    public void handle(KeyEvent e) {
        String temp = searchTF.getSelectedText();
        if(e != null && (temp == null || temp.isEmpty()) && e.getCode() == KeyCode.C && e.isShortcutDown()) 
            copy();
        else if(e != null && (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN)) {
            int index = model.getSelectedIndex();
            if(index < 0)
                index = 0;
            else if(e.getCode() == KeyCode.UP)
                index--;
            else
                index++;
            if(index >= 0 && index < table.getItems().size())
                model.select(index, table.getColumns().get(0));
        } else {
            TempManga manga = model.getSelectedItem();
            model.clearSelection();
            
            String text = searchTF.getText();
            if(text == null || text.isEmpty()) 
                table.getItems().setAll(allData);
            else {
                String t2 = text.toLowerCase();
                String t2iD = text.trim();
                table.getItems().clear();
                allData.stream()
                .filter(s -> s.idString.equals(t2iD) || s.lowerCaseName.contains(t2))
                .forEach(table.getItems()::add);
            }
            if(!table.getItems().isEmpty()) {
                if(manga == null)
                    model.select(0, table.getColumns().get(0));
                else
                    model.select(manga);
            }
        }
        if(table.getItems().isEmpty())
            chapterLabel.setText(null);
    }

    private Node getSelectedListView() {
        ListView<TempManga> view = new ListView<>();
        view.setPlaceholder(new Text("  Nothing \n  selected  "));
        selectedItems = view.getItems();
        view.setPrefWidth(80);
        
       MultipleSelectionModel<TempManga> model = view.getSelectionModel();
        
        BorderPane root = new BorderPane(view);
        Label t = new Label("Selected");
        t.setPadding(new Insets(5));
        t.setTextAlignment(TextAlignment.CENTER);
        t.setAlignment(Pos.CENTER);
        root.setTop(t);
        
        Button remove = FxHelpers.button("remove selected", "delete.png", e -> {
            TempManga manga = model.getSelectedItem();
            model.clearSelection();
            selectedItems.remove(manga);
            allData.add(manga);
            handle(null);
        });
        Button clipboard = FxHelpers.button("copy selected", "clipboard.png", e -> {
            FxClipboard.copyToClipboard(selectedItems.stream().map(t1 -> t1.idString).collect(Collectors.joining(" ")));
            FxPopupShop.showHidePopup("selected copied", 1500);
        });
        remove.visibleProperty().bind(model.selectedItemProperty().isNotNull());
        clipboard.visibleProperty().bind(Bindings.isNotEmpty(selectedItems));
        
        HBox bottom = new HBox(2, remove,clipboard);
        FxHelpers.addClass(bottom, "selecte-btns");
        bottom.setPadding(new Insets(5));
        root.setBottom(bottom);
        
        view.setCellFactory(c -> new ListCell<TempManga>() {
            @Override
            protected void updateItem(TempManga item, boolean empty) {
                super.updateItem(item, empty);

                if(empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item.idString);
                    setTooltip(new Tooltip(item.id+"\n"+item.name+"\n"+item.lastChap.getFileName()));
                }
            }
        });
        return root;
    }

    @Override
    public void stop() throws Exception {
        samrock.close();
        System.exit(0);
        super.stop();
    }

    @SuppressWarnings("unchecked")
    private TableView<TempManga> getTable() {
        TableView<TempManga> table = new TableView<>();
        table.getItems().setAll(allData);
        table.setPrefWidth(350);
        model =  table.getSelectionModel();
        model.setCellSelectionEnabled(true);
        model.setSelectionMode(SelectionMode.SINGLE);
        model.selectedItemProperty().addListener(this);
        table.setEditable(false);
        table.setOnKeyReleased(e -> {
            if(e.getCode() == KeyCode.C && e.isShortcutDown())
                copy();
        });

        TableColumn<TempManga, String> idC = new TableColumn<>("manga_id");
        TableColumn<TempManga, String> nameC = new TableColumn<>("manga_name");

        idC.setCellValueFactory(c -> c.getValue().idProperty());
        nameC.setCellValueFactory(c -> c.getValue().nameProperty());

        table.getColumns().addAll(idC, nameC);

        return table;
    }

    Chapter notchapter = new Chapter(-1, "no last chapter found");
    @Override
    public void changed(ObservableValue<? extends TempManga> observable, TempManga oldValue, TempManga newValue) {
        if(newValue == null) {
            chapterLabel.setText(null);
            return;
        }
        if(newValue.lastChap == null) {
            try {
                newValue.lastChap = samrock.chapter().getLastChapter(newValue.id);
            } catch (SQLException e1) {
                FxPopupShop.showHidePopup("failed to load lastchap: \nmanga_id: "+newValue.id+"  \nmanga_name:"+newValue.name, 2500);
            }
            if(newValue.lastChap == null)
                newValue.lastChap = notchapter;
        }
        chapterLabel.setText(newValue.lastChap.getFileName());
    }
}
