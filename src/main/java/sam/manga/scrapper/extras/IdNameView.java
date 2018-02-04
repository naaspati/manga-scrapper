package sam.manga.scrapper.extras;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

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

import sam.db.sqlite.SqliteManeger;
import sam.properties.myconfig.MyConfig;
import sam.swing.popup.SwingPopupShop;
import sam.swing.utils.SwingUtils;

public class IdNameView extends JFrame implements KeyListener, ListSelectionListener{
    private static final long serialVersionUID = -3846017716203129262L;
    
    private ArrayList<Object[]> data = new ArrayList<>();
    private Object[][] dataBeingDisplayed;
    private HashMap<Integer, Integer> map = new HashMap<>();
    private final JLabel chapterLabel;
    private final JTable table;
    private final JTextField searchTF = new JTextField();

    public IdNameView() throws ClassNotFoundException {
        super("Name list");
        SwingPopupShop.setPopupsRelativeTo(this);
        readDb();
        dataBeingDisplayed = data.toArray(new Object[data.size()][]);

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
            public Object getValueAt(int rowIndex, int columnIndex) {
                return dataBeingDisplayed[rowIndex][columnIndex];
            }

            @Override
            public int getRowCount() {
                return dataBeingDisplayed.length;
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

    private void readDb() {
        try(SqliteManeger c = new SqliteManeger(MyConfig.SAMROCK_DB, true)) {
            c.executeQueryAndIterateResultSet("SELECT * FROM LastChap", rs -> {
                map.put(rs.getInt(1), data.size());
                data.add(new Object[] {rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(2).toLowerCase()});                
            });
        } catch (SQLException | InstantiationException | IllegalAccessException | IOException | ClassNotFoundException e) {
            System.out.println("failed to open samrock connection: "+MyConfig.SAMROCK_DB);
            e.printStackTrace();
            System.exit(0);
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        Object[] s = data.get(map.get(table.getValueAt(table.getSelectedRow(), 0))); 
        chapterLabel.setText(String.valueOf(s[2]));
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
                if(text == null || text.isEmpty())
                    dataBeingDisplayed = data.toArray(new Object[data.size()][]);
                else {
                    String t2 = text.toLowerCase();
                    dataBeingDisplayed = data.stream()
                            .filter(o -> o[3].toString().contains(t2))
                            .toArray(Object[][]::new);
                }
                if(table.getRowCount() > 0) {
                    table.getSelectionModel().setSelectionInterval(0, 0);
                    Object[] s = data.get(map.get(table.getValueAt(0, 0))); 
                    chapterLabel.setText(String.valueOf(s[2]));
                }
                else
                    chapterLabel.setText(null);
            }
            table.revalidate();
            table.repaint();
        }

}
