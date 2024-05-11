import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;

public class StartupWindow {
    private class StartupActionListener implements ActionListener {
        private StartupWindow window;
        private boolean fromFile;

        public StartupActionListener(StartupWindow window, boolean fromFile) {
            this.window = window;
            this.fromFile = fromFile;
        }

        public void actionPerformed(ActionEvent e) {
            this.window.frame.dispose();
            new MainWindow(this.fromFile);
        }
    }

    JFrame frame;

    public StartupWindow() {
        frame = new JFrame("Family Tree");
        Container contents = frame.getContentPane();
        contents.setLayout(new GridBagLayout());
        GridBagConstraints c;
        JLabel label = new JLabel("Family Tree");
        label.setHorizontalAlignment(JLabel.CENTER);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 36));
        c = new GridBagConstraints();
        c.weightx = 1.0;
        c.weighty = 0.5;
        c.gridx = c.gridy = 0;
        c.gridwidth = 2;
        contents.add(label, c);
        JButton newButton = new JButton("New");
        newButton.addActionListener(new StartupActionListener(this, false));
        newButton.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        c = new GridBagConstraints();
        c.weightx = c.weighty = 0.5;
        c.insets = new Insets(10, 10, 10, 5);
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 1;
        contents.add(newButton, c);
        JButton openButton = new JButton("Open");
        openButton.addActionListener(new StartupActionListener(this, true));
        openButton.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        c = new GridBagConstraints();
        c.weightx = c.weighty = 0.5;
        c.insets = new Insets(10, 5, 10, 10);
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 1;
        c.gridy = 1;
        contents.add(openButton, c);
        contents.setPreferredSize(new Dimension(320, 160));
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setVisible(true);
        contents.requestFocusInWindow();
    }
}
