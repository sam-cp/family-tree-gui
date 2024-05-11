import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.xml.sax.SAXException;

public class App {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | UnsupportedLookAndFeelException e) {}
        try {
            FamilyTree.createSchema();
        } catch (SAXException e) {
            JOptionPane.showMessageDialog(null, "The Family Tree DTD could not be parsed.\n\nDetails:\n" + e.getMessage(), "Internal Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        new StartupWindow();
    }
}
