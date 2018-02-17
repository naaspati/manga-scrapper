package sam.manga.scrapper.extras;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import sam.manga.newsamrock.SamrockDB;
import sam.manga.newsamrock.chapters.Chapter;
import sam.manga.newsamrock.mangas.MangasMeta;
import sam.swing.popup.SwingPopupShop;
import sam.swing.utils.SwingUtils;

public class IdNameView extends JFrame implements KeyListener, ListSelectionListener{
    public static void main(String[] args) throws ClassNotFoundException, InstantiationException, IllegalAccessException, SQLException {
        new IdNameView();
    }
    
    private static final long serialVersionUID = -3846017716203129262L;

    private List<TEMP> allData = new ArrayList<>();
    private List<Integer> visibleIndices;
    private final SamrockDB samrock;

    private final JLabel chapterLabel;
    private final JTable table;
    private final JTextField searchTF = new JTextField();
    
    private class TEMP {
        final String name;
        final String lowerCaseName;
        final int id;
        Chapter lastChap;
        public TEMP(String name, int id) {
            this.name = name;
            this.id = id;
            lowerCaseName = name.toLowerCase();
        }
    }

    public IdNameView() throws ClassNotFoundException, SQLException, InstantiationException, IllegalAccessException {
        super("Name list");
        samrock = new SamrockDB();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    samrock.close();
                } catch (Exception e2) {}
            }
        });

        SwingPopupShop.setPopupsRelativeTo(this);

        samrock.manga().selectAll(rs -> allData.add(new TEMP(rs.getString(MangasMeta.MANGA_NAME), rs.getInt(MangasMeta.MANGA_ID))), MangasMeta.MANGA_ID, MangasMeta.MANGA_NAME);
        visibleIndices = IntStream.range(0, allData.size()).boxed().collect(Collectors.toList()); 

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        table = getTable();
        chapterLabel = new JLabel();
        JPanel panel = new JPanel(new BorderLayout(5, 5), false);
        panel.add(chapterLabel, BorderLayout.NORTH);
        panel.add(searchTF, BorderLayout.SOUTH);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        searchTF.getActionMap().put("copy", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    String str = table.getValueAt(table.getSelectedRow(), table.getSelectedColumn()).toString();
                    SwingUtils.copyToClipBoard(str);
                    SwingPopupShop.showHidePopup(str, 1000);
                } catch (Exception e2) {}
            }
        });
        searchTF.addKeyListener(this);

        searchTF.setFont(table.getFont());
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(panel, BorderLayout.SOUTH);
        setSize(600, 800);
        setLocationRelativeTo(null);
        setVisible(true);
    }
    private JTable getTable() {
        TableModel model =  new AbstractTableModel() {
            private static final long serialVersionUID = 1L;
            String[] columns = {"id", "name"};
            @Override
            public String getColumnName(int column) {
                return columns[column];
            }

            @Override
            public Object getValueAt(int rowIndex, int index) {
                TEMP t = allData.get(visibleIndices.get(rowIndex)); 
                return index == 0 ? t.id : t.name;
            }

            @Override
            public int getRowCount() {
                return visibleIndices.size();
            }
            @Override
            public int getColumnCount() {
                return 2;
            }
        };

        JTable table = new JTable(model);
        table.doLayout();
        table.setCellSelectionEnabled(true);
        table.setColumnSelectionAllowed(true);
        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFont(new Font("Consolas", Font.PLAIN, 20));
        table.setRowHeight(30);
        table.getColumnModel().getColumn(0).setMaxWidth(100);
        table.getSelectionModel().addListSelectionListener(this);

        return table;
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        TEMP t = null;
        try {
            t = allData.get(visibleIndices.get(table.getSelectedRow())); 
        } catch (IndexOutOfBoundsException e2) {
            chapterLabel.setText("");
            return;
        }
        if(t.lastChap == null) {
            try {
                t.lastChap = samrock.chapter().getLastChapter(t.id);
            } catch (SQLException e1) {
                System.out.println("failed to load lastchap: manga: "+t.id+"  "+t.name);
                chapterLabel.setText("");
                return;
            }
        }
        chapterLabel.setText(t.lastChap.getFileName());
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {}


    @Override
    public void keyReleased(KeyEvent e) {
        int rIndex = table.getSelectedRow();
        if(e.getKeyCode() == KeyEvent.VK_UP) {
            if(--rIndex >= 0)
                table.getSelectionModel().setSelectionInterval(rIndex, rIndex);
        }
        else if(e.getKeyCode() == KeyEvent.VK_DOWN) {
            if(++rIndex <= table.getRowCount() - 1)
                table.getSelectionModel().setSelectionInterval(rIndex, rIndex);
        }
        else {
            String text = searchTF.getText();
            if(text == null || text.isEmpty()) {
                visibleIndices = IntStream.range(0, allData.size()).boxed().collect(Collectors.toList());;
            }
            else {
                String t2 = text.toLowerCase();
                
                visibleIndices = IntStream.range(0, allData.size())
                .filter(i -> allData.get(i).lowerCaseName.contains(t2))
                .boxed()
                .collect(Collectors.toList());
            }
            if(table.getRowCount() > 0) {
                table.getSelectionModel().clearSelection();
                table.getSelectionModel().setSelectionInterval(0, 0);
            }
            else
                chapterLabel.setText(null);
        }
        table.revalidate();
        table.repaint();
    }

}
