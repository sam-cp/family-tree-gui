import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

public class MainWindow {
    private FamilyTree familyTree;
    private JFrame frame;
    private JList<UUID> memberList;
    private PersonListModel memberListModel;
    private PersonEditor personEditor;
    private TreeView treeView;
    private JButton addButton;
    private JTextField searchField;
    private File xml;
    private boolean changed;

    private class PersonListModel extends AbstractListModel<UUID> {
        ArrayList<UUID> options;
        String filterString;

        public PersonListModel(FamilyTree familyTree) {
            this.options = new ArrayList<UUID>(familyTree.getMembers());
            this.options.sort(new NameComparator(familyTree));
            this.filterString = "";
        }

        public void filter(String filterString) {
            this.filterString = filterString;
            this.update();
        }

        private class NonMatchingPredicate implements Predicate<UUID> {
            FamilyTree familyTree;
            String filter;

            public NonMatchingPredicate(FamilyTree familyTree, String filter) {
                this.familyTree = familyTree;
                this.filter = filter;
            }

            @Override
            public boolean test(UUID id) {
                return !this.familyTree.getPerson(id).matches(this.filter);
            }
        }

        public void update() {
            int prevSize = this.options.size();
            this.options.clear();
            this.options.addAll(familyTree.getMembers());
            this.options.removeIf(new NonMatchingPredicate(familyTree, filterString));
            this.options.sort(new NameComparator(familyTree));
            this.fireContentsChanged(familyTree, 0, getSize() - 1);
            if (prevSize > this.options.size()) {
                this.fireIntervalRemoved(familyTree, this.options.size(), prevSize - 1);
            }
        }

        @Override
        public int getSize() {
            return options.size();
        }

        @Override
        public UUID getElementAt(int index) {
            if (index < 0 || index >= this.options.size()) {
                return null;
            }
            return options.get(index);
        }
    }

    private class PersonListCellRenderer extends JPanel implements ListCellRenderer<UUID> {
        FamilyTree familyTree;
        JLabel nameLabel;

        private static final Color SELECTED_COLOR = new Color(240, 240, 240);

        public PersonListCellRenderer(FamilyTree familyTree) {
            this.familyTree = familyTree;
            this.nameLabel = new JLabel();
            this.nameLabel.setPreferredSize(new Dimension(250, 75));
            this.nameLabel.setMinimumSize(this.nameLabel.getPreferredSize());
            this.nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
            this.nameLabel.setVerticalAlignment(SwingConstants.CENTER);
            this.setLayout(new BorderLayout());
            this.add(this.nameLabel, BorderLayout.CENTER);
            this.setBorder(new MatteBorder(0, 0, 1, 0, new Color(224, 224, 224)));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends UUID> list, UUID value, int index,
                boolean isSelected, boolean cellHasFocus) {
            String name = familyTree.getName(value);
            if (name == null) {
                name = "[Unnamed]";
            }
            this.nameLabel.setText(name);
            this.setBackground(isSelected ? PersonListCellRenderer.SELECTED_COLOR: Color.WHITE);
            return this;
        }
    }

    private class PersonListSelectionListener implements ListSelectionListener {
        MainWindow parent;

        public PersonListSelectionListener(MainWindow parent) {
            parent.memberList.addListSelectionListener(this);
            this.parent = parent;
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            int newIndex = parent.memberList.getSelectedIndex();
            UUID id = parent.memberListModel.getElementAt(newIndex);
            this.parent.personEditor.setSubject(id);
            this.parent.treeView.setSubject(id);
        }
    }

    private class PersonEditor extends JPanel {
        UUID id;
        MainWindow parent;
        NameField firstName, middleName, lastName, preferredName, maidenName;
        JComboBox<UUIDWrapper> father, mother, spouse;
        JLabel genderLabel;
        JButton deleteButton;

        private class UUIDWrapper {
            UUID uuid;
            private FamilyTree familyTree;

            public UUIDWrapper(UUID uuid, FamilyTree familyTree) {
                this.uuid = uuid;
                this.familyTree = familyTree;
            }

            @Override
            public boolean equals(Object other) {
                if (!(other instanceof UUIDWrapper)) {
                    return false;
                }
                UUIDWrapper _other = (UUIDWrapper) other;
                if (_other.uuid == null || this.uuid == null) {
                    return (_other.uuid == null) == (this.uuid == null);
                }
                return _other.uuid.equals(this.uuid);
            }

            @Override
            public String toString() {
                if (this.uuid == null) {
                    return "[None]";
                }
                String name = this.familyTree.getName(uuid);
                if (name == null) {
                    name = "[Unnamed]";
                }
                return name;
            }
        }

        private enum FamilyComboBoxType {
            FATHER, MOTHER, SPOUSE
        }

        private class FamilyComboBoxModel extends DefaultComboBoxModel<UUIDWrapper> {
            public FamilyComboBoxModel(FamilyTree familyTree, UUID subject, FamilyComboBoxType type) {
                super();
                if (subject == null) {
                    return;
                }

                TreeSet<UUID> optionSet = familyTree.getMembers();
                optionSet.remove(subject);

                Gender _targetGender = null;
                if (type == FamilyComboBoxType.FATHER) {
                    _targetGender = Gender.MALE;
                } else if (type == FamilyComboBoxType.MOTHER) {
                    _targetGender = Gender.FEMALE;
                } else if (type == FamilyComboBoxType.SPOUSE) {
                    switch (familyTree.getPerson(subject).getGender()) {
                    case MALE:
                        _targetGender = Gender.FEMALE;
                        break;
                    case FEMALE:
                        _targetGender = Gender.MALE;
                        break;
                    }
                }
                final Gender targetGender = _targetGender;
                optionSet.removeIf(new Predicate<UUID>() {
                    @Override
                    public boolean test(UUID t) {
                        return familyTree.getPerson(t).getGender() != targetGender;
                    }
                });

                if (type == FamilyComboBoxType.FATHER || type == FamilyComboBoxType.MOTHER) {
                    optionSet.removeIf(new Predicate<UUID>() {
                        @Override
                        public boolean test(UUID t) {
                            return familyTree.isDescendant(t, subject);
                        }
                    });
                }

                ArrayList<UUID> options = new ArrayList<UUID>(optionSet);
                options.sort(new NameComparator(familyTree));

                ArrayList<UUIDWrapper> wOptions = new ArrayList<UUIDWrapper>();
                wOptions.add(new UUIDWrapper(null, familyTree));
                for (UUID id : options) {
                    wOptions.add(new UUIDWrapper(id, familyTree));
                }

                super.addAll(wOptions);

                switch (type) {
                case FATHER:
                    super.setSelectedItem(new UUIDWrapper(familyTree.getFather(subject), familyTree));
                    break;
                case MOTHER:
                    super.setSelectedItem(new UUIDWrapper(familyTree.getMother(subject), familyTree));
                    break;
                case SPOUSE:
                    super.setSelectedItem(new UUIDWrapper(familyTree.getSpouse(subject), familyTree));
                    break;
                }
            }
        }

        private enum NameType {
            FIRST_NAME, MIDDLE_NAME, LAST_NAME, PREFERRED_NAME, MAIDEN_NAME;
        }

        private class NameField extends JTextField {
            private PersonEditor parent;
            private NameType type;
            private boolean active;

            public NameField(PersonEditor parent, NameType type) {
                this.parent = parent;
                this.type = type;
                this.active = false;
                super.getDocument().addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        if (active) {
                            updateName();
                        }
                    }
                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        if (active) {
                            updateName();
                        }
                    }
                    @Override
                    public void changedUpdate(DocumentEvent e) {}
                });
            }

            private void setNameText(String text) {
                this.active = false;
                super.setText(text);
                this.active = true;
            }

            private String getNameText() {
                String text = super.getText();
                if (text == null || text.isEmpty()) {
                    return null;
                }
                return text;
            }

            private void updateName() {
                if (parent.id == null) {
                    return;
                }
                Person p = parent.parent.familyTree.getPerson(parent.id);
                if (p == null) {
                    return;
                }
                switch (this.type) {
                case FIRST_NAME:
                    p.firstName = this.getNameText();
                    break;
                case MIDDLE_NAME:
                    p.middleName = this.getNameText();
                    break;
                case LAST_NAME:
                    p.lastName = this.getNameText();
                    break;
                case PREFERRED_NAME:
                    p.nickname = this.getNameText();
                    break;
                case MAIDEN_NAME:
                    p.maidenName = this.getNameText();
                    break;
                }
                parent.parent.notifyChange();
            }
        }

        public PersonEditor(MainWindow parent) {
            this.parent = parent;
            this.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            this.setLayout(new GridLayout(1, 5, 10, 10));
            JPanel nameLabels = new JPanel(new GridLayout(5, 1, 0, 5));
            this.add(nameLabels);
            nameLabels.add(new JLabel("First Name: ", SwingConstants.TRAILING));
            nameLabels.add(new JLabel("Middle Name: ", SwingConstants.TRAILING));
            nameLabels.add(new JLabel("Last Name: ", SwingConstants.TRAILING));
            nameLabels.add(new JLabel("Preferred Name: ", SwingConstants.TRAILING));
            nameLabels.add(new JLabel("Maiden Name: ", SwingConstants.TRAILING));
            JPanel nameTextFields = new JPanel(new GridLayout(5, 1, 0, 5));
            this.add(nameTextFields);

            nameTextFields.add(this.firstName = new NameField(this, NameType.FIRST_NAME));
            nameTextFields.add(this.middleName = new NameField(this, NameType.MIDDLE_NAME));
            nameTextFields.add(this.lastName = new NameField(this, NameType.LAST_NAME));
            nameTextFields.add(this.preferredName = new NameField(this, NameType.PREFERRED_NAME));
            nameTextFields.add(this.maidenName = new NameField(this, NameType.MAIDEN_NAME));
            JPanel familyLabels = new JPanel(new GridLayout(3, 1, 0, 5));
            this.add(familyLabels);
            familyLabels.add(new JLabel("Father: ", SwingConstants.TRAILING));
            familyLabels.add(new JLabel("Mother: ", SwingConstants.TRAILING));
            familyLabels.add(new JLabel("Spouse: ", SwingConstants.TRAILING));
            JPanel familyBoxes = new JPanel(new GridLayout(3, 1, 0, 5));
            this.add(familyBoxes);
            familyBoxes.add(this.father = new JComboBox<UUIDWrapper>());
            familyBoxes.add(this.mother = new JComboBox<UUIDWrapper>());
            familyBoxes.add(this.spouse = new JComboBox<UUIDWrapper>());
            JPanel buttonPanel = new JPanel(new GridLayout(2, 1));
            this.add(buttonPanel);
            buttonPanel.add(this.genderLabel = new JLabel("", SwingConstants.LEADING));
            buttonPanel.add(this.deleteButton = new JButton("Delete"));
            this.deleteButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (parent.confirmDelete()) {
                        parent.familyTree.removePerson(getSubject());
                        parent.notifyChange();
                        if (parent.memberList.getSelectedIndex() == -1) {
                            parent.memberList.setSelectedIndex(parent.familyTree.size() - 1);
                        }
                        parent.treeView.setSubject(parent.memberList.getSelectedValue());
                        setSubject(parent.memberList.getSelectedValue());
                    }
                }
            });
            this.father.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        UUIDWrapper item = (UUIDWrapper) e.getItem();
                        if (item.uuid == null) {
                            parent.familyTree.removeFather(id);
                        } else {
                            parent.familyTree.setFather(id, item.uuid);
                        }
                        parent.notifyChange();
                    }
                }
            });
            this.mother.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        UUIDWrapper item = (UUIDWrapper) e.getItem();
                        if (item.uuid == null) {
                            parent.familyTree.removeMother(getSubject());
                        } else {
                            parent.familyTree.setMother(getSubject(), item.uuid);
                        }
                        parent.notifyChange();
                    }
                }
            });
            this.spouse.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        UUIDWrapper item = (UUIDWrapper) e.getItem();
                        if (item.uuid == null) {
                            parent.familyTree.removeSpouse(getSubject());
                        } else {
                            parent.familyTree.setSpouse(getSubject(), item.uuid);
                        }
                        parent.notifyChange();
                    }
                }
            });
            this.setSubject(null);
        }

        private UUID getSubject() {
            return this.id;
        }

        public void setSubject(UUID id) {
            if (id == this.id && id != null) {
                return;
            }
            this.id = id;
            this.father.setModel(new FamilyComboBoxModel(this.parent.familyTree, id, FamilyComboBoxType.FATHER));
            this.mother.setModel(new FamilyComboBoxModel(this.parent.familyTree, id, FamilyComboBoxType.MOTHER));
            this.spouse.setModel(new FamilyComboBoxModel(this.parent.familyTree, id, FamilyComboBoxType.SPOUSE));
            if (id == null) {
                this.genderLabel.setText("Gender: ");
                this.firstName.setNameText(null);
                this.middleName.setNameText(null);
                this.lastName.setNameText(null);
                this.preferredName.setNameText(null);
                this.maidenName.setNameText(null);
                this.setVisible(false);
            } else {
                Person p = parent.familyTree.getPerson(id);
                switch (p.getGender()) {
                case MALE:
                    this.genderLabel.setText("Gender: Male");
                    break;
                case FEMALE:
                    this.genderLabel.setText("Gender: Female");
                    break;
                }
                this.firstName.setNameText(p.firstName);
                this.middleName.setNameText(p.middleName);
                this.lastName.setNameText(p.lastName);
                this.preferredName.setNameText(p.nickname);
                this.maidenName.setNameText(p.maidenName);
                this.setVisible(true);
            }
        }
    }

    private class MainWindowListener implements WindowListener {
        private MainWindow parent;

        public MainWindowListener(MainWindow parent) {
            this.parent = parent;
            frame.addWindowListener(this);
        }
        @Override
        public void windowOpened(WindowEvent e) {}

        @Override
        public void windowClosing(WindowEvent e) {
            if (this.parent.changed) {
                int saveResult = JOptionPane.showConfirmDialog(this.parent.frame,
                    "Do you want to save your family tree? You might have unsaved changes.",
                    "Save Family Tree?",
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (saveResult == JOptionPane.CANCEL_OPTION) {
                    return;
                } else if (saveResult == JOptionPane.YES_OPTION) {
                    this.parent.saveFile();
                }
            }
            this.parent.frame.dispose();
        }

        @Override
        public void windowClosed(WindowEvent e) {}

        @Override
        public void windowIconified(WindowEvent e) {}

        @Override
        public void windowDeiconified(WindowEvent e) {}

        @Override
        public void windowActivated(WindowEvent e) {}

        @Override
        public void windowDeactivated(WindowEvent e) {}
    }

    private class TreeView extends JPanel {
        private MainWindow parent;
        private UUID subject;
        private ArrayList<Box> members;
        private int minX, minY, maxX, maxY, boxCoord;

        private class Box {
            UUID id;
            Point loc;
            Point spouse;
            ArrayList<Point> relatives;
            
            public Box(UUID id, int x, int y) {
                this.id = id;
                this.loc = new Point(x, y);
                this.spouse = null;
                this.relatives = new ArrayList<Point>();
            }
        }

        public TreeView(MainWindow parent) {
            this.parent = parent;
            this.subject = null;
            this.members = new ArrayList<Box>();

            this.addMouseListener(new MouseListener() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Box selected = null;
                    for (Box b : members) {
                        int bx = getWidth() / 2 + (2 * boxCoord * b.loc.x - 2 * boxCoord * (minX + maxX) / 2);
                        int by = getHeight() / 2 - (4 * boxCoord * b.loc.y - 4 * boxCoord * (minY + maxY) / 2);
                        if (e.getX() <= bx + boxCoord * 3 / 2 && e.getX() >= bx - boxCoord * 3 / 2 &&
                            e.getY() <= by + boxCoord && e.getY() >= by - boxCoord) {
                            selected = b;
                            break;
                        }
                    }
                    if (selected != null && selected.id != subject) {
                        parent.setSubject(selected.id);
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {}
                @Override
                public void mouseReleased(MouseEvent e) {}
                @Override
                public void mouseEntered(MouseEvent e) {}
                @Override
                public void mouseExited(MouseEvent e) {}
            });
            this.addMouseMotionListener(new MouseMotionListener() {
                @Override
                public void mouseDragged(MouseEvent e) {}

                @Override
                public void mouseMoved(MouseEvent e) {
                    Box selected = null;
                    for (Box b : members) {
                        int bx = getWidth() / 2 + (2 * boxCoord * b.loc.x - 2 * boxCoord * (minX + maxX) / 2);
                        int by = getHeight() / 2 - (4 * boxCoord * b.loc.y - 4 * boxCoord * (minY + maxY) / 2);
                        if (e.getX() <= bx + boxCoord * 3 / 2 && e.getX() >= bx - boxCoord * 3 / 2 &&
                            e.getY() <= by + boxCoord && e.getY() >= by - boxCoord) {
                            selected = b;
                            break;
                        }
                    }
                    if (selected != null && selected.id != subject) {
                        setCursor(new Cursor(Cursor.HAND_CURSOR));
                    } else {
                        setCursor(null);
                    }
                }
            });
        }

        public void reload() {
            this.members.clear();
            if (this.subject != null && this.parent.familyTree.hasPerson(this.subject)) {
                Box box = new Box(this.subject, 0, 0);

                UUID spouse = this.parent.familyTree.getSpouse(this.subject);
                Box spouseBox = null;
                if (spouse != null) {
                    switch (this.parent.familyTree.getPerson(spouse).getGender()) {
                    case MALE:
                        spouseBox = new Box(spouse, -2, 0);
                        break;
                    case FEMALE:
                        spouseBox = new Box(spouse, 2, 0);
                        break;
                    }
                    spouseBox.spouse = box.loc;
                    this.members.add(spouseBox);
                }
                Object[] children = this.parent.familyTree.getChildren(this.subject).toArray();
                for (int i = 0; i < children.length; i++) {
                    Box childBox = new Box((UUID) children[i], 2 * i - children.length + 1, -1);
                    Point parentPt = box.loc;
                    if (spouse != null) {
                        boolean isChild = false;
                        switch (this.parent.familyTree.getPerson(spouse).getGender()) {
                        case MALE:
                            isChild = this.parent.familyTree.getFather((UUID) children[i]) == spouse;
                            break;
                        case FEMALE:
                            isChild = this.parent.familyTree.getMother((UUID) children[i]) == spouse;
                            break;
                        }
                        if (isChild) {
                            parentPt = new Point((box.loc.x + spouseBox.loc.x) / 2, (box.loc.y + spouseBox.loc.y) / 2);
                        }
                    }
                    childBox.relatives.add(parentPt);
                    this.members.add(childBox);
                }

                UUID father = this.parent.familyTree.getFather(this.subject);
                UUID mother = this.parent.familyTree.getMother(this.subject);
                Box fatherBox = null;
                Box motherBox = null;
                this.members.add(box);
                TreeSet<UUID> fatherHalfSiblings = new TreeSet<UUID>();
                if (father != null) {
                    fatherBox = new Box(father, -1, 1);
                    this.members.add(fatherBox);
                    UUID gf = this.parent.familyTree.getFather(father);
                    UUID gm = this.parent.familyTree.getMother(father);
                    Box gfBox = null;
                    Box gmBox = null;
                    if (gf != null) {
                        gfBox = new Box(gf, -4, 2);
                        this.members.add(gfBox);
                    }
                    if (gm != null) {
                        gmBox = new Box(gm, -2, 2);
                        this.members.add(gmBox);
                    }
                    if (gf != null && gm != null) {
                        fatherBox.relatives.add(new Point((gfBox.loc.x + gmBox.loc.x) / 2, (gfBox.loc.y + gmBox.loc.y) / 2));
                        if (this.parent.familyTree.getSpouse(gf) == gm) {
                            gfBox.spouse = gmBox.loc;
                        } else {
                            gfBox.relatives.add(gmBox.loc);
                        }
                    } else {
                        if (gf != null) {
                            fatherBox.relatives.add(gfBox.loc);
                        }
                        if (gm != null) {
                            fatherBox.relatives.add(gmBox.loc);
                        }
                    }
                    fatherHalfSiblings = this.parent.familyTree.getChildren(father);
                }
                TreeSet<UUID> motherHalfSiblings = new TreeSet<UUID>();
                if (mother != null) {
                    motherBox = new Box(mother, 1, 1);
                    this.members.add(motherBox);
                    UUID gf = this.parent.familyTree.getFather(mother);
                    UUID gm = this.parent.familyTree.getMother(mother);
                    Box gfBox = null;
                    Box gmBox = null;
                    if (gf != null) {
                        gfBox = new Box(gf, 2, 2);
                        this.members.add(gfBox);
                    }
                    if (gm != null) {
                        gmBox = new Box(gm, 4, 2);
                        this.members.add(gmBox);
                    }
                    if (gf != null && gm != null) {
                        motherBox.relatives.add(new Point((gfBox.loc.x + gmBox.loc.x) / 2, (gfBox.loc.y + gmBox.loc.y) / 2));
                        if (this.parent.familyTree.getSpouse(gf) == gm) {
                            gfBox.spouse = gmBox.loc;
                        } else {
                            gfBox.relatives.add(gmBox.loc);
                        }
                    } else {
                        if (gf != null) {
                            motherBox.relatives.add(gfBox.loc);
                        }
                        if (gm != null) {
                            motherBox.relatives.add(gmBox.loc);
                        }
                    }
                    motherHalfSiblings = this.parent.familyTree.getChildren(mother);
                }
                fatherHalfSiblings.remove(this.subject);
                motherHalfSiblings.remove(this.subject);
                TreeSet<UUID> siblings = new TreeSet<UUID>();
                for (UUID id : fatherHalfSiblings) {
                    if (motherHalfSiblings.contains(id)) {
                        siblings.add(id);
                    }
                }
                for (UUID id : siblings) {
                    fatherHalfSiblings.remove(id);
                    motherHalfSiblings.remove(id);
                }
                int siblingStart = 0;
                int fatherStart = 0;
                int motherStart = 0;
                switch (this.parent.familyTree.getPerson(this.subject).getGender()) {
                case MALE:
                    siblingStart = -siblings.size() * 2 - 1;
                    fatherStart = siblingStart - fatherHalfSiblings.size() * 2;
                    if (!siblings.isEmpty()) {
                        fatherStart -= 1;
                    }
                    motherStart = 3;
                    if (spouse != null) {
                        motherStart += 2;
                    }
                    break;
                case FEMALE:
                    siblingStart = 3;
                    motherStart = 3 + siblings.size() * 2;
                    if (!siblings.isEmpty()) {
                        motherStart += 1;
                    }
                    fatherStart = -fatherHalfSiblings.size() * 2 - 1;
                    if (spouse != null) {
                        fatherStart -= 2;
                    }
                    break;
                }
                if (father != null && mother != null) {
                    Point mid = new Point((fatherBox.loc.x + motherBox.loc.x) / 2, (fatherBox.loc.y + motherBox.loc.y) / 2);
                    box.relatives.add(mid);
                    if (this.parent.familyTree.getSpouse(father) == mother) {
                        fatherBox.spouse = motherBox.loc;
                    } else {
                        fatherBox.relatives.add(motherBox.loc);
                    }
                    int i = siblingStart;
                    for (UUID id : siblings) {
                        Box siblingBox = new Box(id, i, 0);
                        siblingBox.relatives.add(mid);
                        this.members.add(siblingBox);
                        i += 2;
                    }
                } else {
                    if (father != null) {
                        box.relatives.add(fatherBox.loc);
                    }
                    if (mother != null) {
                        box.relatives.add(motherBox.loc);
                    }
                    int i = siblingStart;
                    for (UUID id : siblings) {
                        Box siblingBox = new Box(id, i, 0);
                        if (father != null) {
                            siblingBox.relatives.add(fatherBox.loc);
                        }
                        if (mother != null) {
                            siblingBox.relatives.add(motherBox.loc);
                        }
                        this.members.add(siblingBox);
                        i += 2;
                    }
                }
                int i = fatherStart;
                for (UUID id : fatherHalfSiblings) {
                    Box siblingBox = new Box(id, i, 0);
                    siblingBox.relatives.add(fatherBox.loc);
                    this.members.add(siblingBox);
                    i += 2;
                }
                i = motherStart;
                for (UUID id : motherHalfSiblings) {
                    Box siblingBox = new Box(id, i, 0);
                    siblingBox.relatives.add(motherBox.loc);
                    this.members.add(siblingBox);
                    i += 2;
                }
            }
            this.repaint();
        }

        public void setSubject(UUID subject) {
            this.subject = subject;
            this.reload();
        }

        public void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g;
            int width = this.getWidth();
            int height = this.getHeight();
            g.setColor(new Color(248, 248, 248));
            g.fillRect(0, 0, width, height);
            minX = minY = Integer.MAX_VALUE;
            maxX = maxY = Integer.MIN_VALUE;

            for (Box b : this.members) {
                if (b.loc.x < minX) {
                    minX = b.loc.x;
                }
                if (b.loc.x > maxX) {
                    maxX = b.loc.x;
                }
                if (b.loc.y < minY) {
                    minY = b.loc.y;
                }
                if (b.loc.y > maxY) {
                    maxY = b.loc.y;
                }
            }
            
            boxCoord = Math.min(width / ((maxX - minX) * 2 + 4), height / ((maxY - minY) * 4 + 3));
            g2d.setColor(Color.BLACK);
            int precision = Math.max(boxCoord / 3, 1);
            g2d.setStroke(new BasicStroke(3));
            for (Box b : this.members) {
                int bx = width / 2 + (2 * boxCoord * b.loc.x - 2 * boxCoord * (minX + maxX) / 2);
                int by = height / 2 - (4 * boxCoord * b.loc.y - 4 * boxCoord * (minY + maxY) / 2);
                Point bpt = new Point(bx, by);
                for (Point p : b.relatives) {
                    int px = width / 2 + (2 * boxCoord * p.x - 2 * boxCoord * (minX + maxX) / 2);
                    int py = height / 2 - (4 * boxCoord * p.y - 4 * boxCoord * (minY + maxY) / 2);
                    int yavg = (by + py) / 2;
                    Point bezier[] = new Point[precision + 1];
                    for (int i = 0; i <= precision; i++) {
                        bezier[i] = cubicBezier(precision, i, bpt, new Point(bx, yavg), new Point(px, yavg), new Point(px, py));
                    }
                    for (int i = 0; i < precision; i++) {
                        g2d.drawLine(bezier[i].x, bezier[i].y, bezier[i + 1].x, bezier[i + 1].y);
                    }
                }
                if (b.spouse != null) {
                    int sx = width / 2 + (2 * boxCoord * b.spouse.x - 2 * boxCoord * (minX + maxX) / 2);
                    int sy = height / 2 - (4 * boxCoord * b.spouse.y - 4 * boxCoord * (minY + maxY) / 2);
                    g2d.drawLine(bx, by, sx, sy);
                    g2d.drawLine(bx, by - 6, sx, sy - 6);
                }
            }
            for (Box b : this.members) {
                drawBox(g2d, b.id,
                    width / 2 + (2 * boxCoord * b.loc.x - 2 * boxCoord * (minX + maxX) / 2),
                    height / 2 - (4 * boxCoord * b.loc.y - 4 * boxCoord * (minY + maxY) / 2),
                    boxCoord * 3, boxCoord * 2);
            }
        }

        private Point cubicBezier(int precision, int t, Point start, Point c1, Point c2, Point end) {
            int k = precision - t;
            int m1 = k * k * k;
            int m2 = 3 * k * k * t;
            int m3 = 3 * k * t * t;
            int m4 = t * t * t;
            int x = m1 * start.x + m2 * c1.x + m3 * c2.x + m4 * end.x;
            int y = m1 * start.y + m2 * c1.y + m3 * c2.y + m4 * end.y;
            int p = precision * precision * precision;
            return new Point(x / p, y / p);
        }

        private void drawBox(Graphics2D g2d, UUID id, int centerX, int centerY, int boxW, int boxH) {
            g2d.setColor(Color.WHITE);
            int boxRad = boxH / 6;
            g2d.fillRoundRect(centerX - boxW / 2, centerY - boxH / 2, boxW, boxH, boxRad, boxRad);
            Person p = this.parent.familyTree.getPerson(id);
            switch (p.getGender()) {
            case MALE:
                g2d.setColor(new Color(128, 192, 255));
                break;
            case FEMALE:
                g2d.setColor(new Color(255, 128, 192));
                break;
            }
            g2d.setStroke(new BasicStroke(5));
            g2d.drawRoundRect(centerX - boxW / 2, centerY - boxH / 2, boxW, boxH, boxRad, boxRad);
            String name = this.parent.familyTree.getName(id);
            if (name == null) {
                name = "[Unnamed]";
            }
            drawText(g2d, name, centerX, centerY, boxW - 2 * boxRad, boxH - 2 * boxRad);
        }

        private void drawText(Graphics2D g2d, String text, int cx, int cy, int width, int height) {
            Font font = new Font(Font.SANS_SERIF, Font.PLAIN, height / 4);
            FontMetrics fm = g2d.getFontMetrics(font);
            ArrayList<String> lines = new ArrayList<String>();
            String trimmed = text.trim();
            String[] words = trimmed.split("\\s+");
            for (String w : words) {
                if (lines.isEmpty() || fm.stringWidth(lines.get(lines.size() - 1) + " " + w) >= width) {
                    lines.add(w);
                } else {
                    lines.set(lines.size() - 1, lines.get(lines.size() - 1) + " " + w);
                }
            }
            AffineTransform transform = g2d.getTransform();
            g2d.translate(cx - width / 2, cy - height * (4 * lines.size() - 1) / 24);
            for (int i = 0; i < lines.size(); i++) {
                JLabel l = new JLabel(lines.get(i));
                l.setFont(font);
                l.setSize(width, height / 4);
                l.setHorizontalAlignment(JLabel.CENTER);
                l.paint(g2d);
                g2d.translate(0, height / 3);
            }
            g2d.setTransform(transform);
        }
    }

    private boolean confirmDelete() {
        return JOptionPane.showConfirmDialog(this.frame,
            "Are you sure you want to remove this person from the family tree?",
            "Confirm Removal",
            JOptionPane.YES_NO_OPTION, 
            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private boolean saveFile() {
        try {
            this.familyTree.checkSave();
        } catch (InvalidFamilyTreeException e) {
            JOptionPane.showMessageDialog(this.frame,
                e.getMessage(),
                "Invalid Family Tree",
                JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (this.xml == null) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new FileNameExtensionFilter("Family Tree files (*.ftr)", "ftr"));
            int result = fileChooser.showSaveDialog(this.frame);
            if (result != JFileChooser.APPROVE_OPTION) {
                System.gc();
                return false;
            }
            this.xml = fileChooser.getSelectedFile();
            if (this.xml.exists()) {
                if (JOptionPane.showConfirmDialog(this.frame,
                    "\"" + this.xml.getName() + "\" already exists. Do you want to overwrite the file?",
                    "Warning: File Exists",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE) == JOptionPane.NO_OPTION) {
                    return false;
                }
            } else if (this.xml.getName().lastIndexOf('.') == -1) {
                this.xml.renameTo(new File(this.xml.getAbsolutePath() + ".ftr"));
            }
        }
        try {
            this.familyTree.saveToFile(xml);
        } catch (FileNotFoundException e) {
            JOptionPane.showMessageDialog(this.frame,
                "You cannot write to \"" + this.xml.getName() + "\".\n\nDetails:\n" + e.getMessage(),
                "Save Failed",
                JOptionPane.ERROR_MESSAGE);
            return false;
        } catch (ParserConfigurationException e) {
            JOptionPane.showMessageDialog(this.frame,
                "Could not create .ftr file.\n\nDetails:\n" + e.getMessage(),
                "Save Failed",
                JOptionPane.ERROR_MESSAGE);
        }
        this.changed = false;
        this.frame.setTitle((this.xml != null) ? this.xml.getName() : "New Family Tree");
        return true;
    }

    public MainWindow(boolean fromFile) {
        this.xml = null;
        if (fromFile) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new FileNameExtensionFilter("Family Tree files (*.ftr)", "ftr"));
            if (fileChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
                System.gc();
                return;
            }
            this.xml = fileChooser.getSelectedFile();
            try {
                this.familyTree = new FamilyTree(this.xml);
            } catch (ParserConfigurationException e) {
                JOptionPane.showMessageDialog(null,
                    "It looks like your system is not set up to read .ftr files.\n\nDetails:\n" + e.getMessage(),
                    "Could not parse file.",
                    JOptionPane.ERROR_MESSAGE);
                return;
            } catch (SAXException e) {
                JOptionPane.showMessageDialog(null,
                    "It looks like \"" + this.xml.getName() + "\" is not in the right format or got corrupted.\n\nDetails:\n" + e.getMessage(),
                    "Could not parse file.",
                    JOptionPane.ERROR_MESSAGE);
                return;
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null,
                    "The file \"" + this.xml.getName() + "\" could not be opened.\n\nDetails:\n" + e.getMessage(),
                    "Could not read file.",
                    JOptionPane.ERROR_MESSAGE);
                return;
            } catch (InvalidFormatException e) {
                JOptionPane.showMessageDialog(null,
                    "The file \"" + this.xml.getName() + "\" does not represent a valid family tree.\n\nDetails:\n" + e.getMessage(),
                    "Could not create family tree.",
                    JOptionPane.ERROR_MESSAGE);
                    return;
            } catch (CycleException e) {
                String msg = e.getMessage() + "\n\nDetails: the cycle contains the following UUIDs:";
                for (UUID id : e.getIDs()) {
                    msg += "\n" + id.toString();
                }
                JOptionPane.showMessageDialog(null,
                    msg,
                    "Could not create family tree.",
                    JOptionPane.ERROR_MESSAGE);
                    return;
            }
        } else {
            this.familyTree = new FamilyTree();
        }
        this.changed = false;
        JPanel listPanel = new JPanel(new BorderLayout());
        this.memberListModel = new PersonListModel(this.familyTree);
        this.memberList = new JList<UUID>(this.memberListModel);
        this.memberList.setCellRenderer(new PersonListCellRenderer(this.familyTree));
        this.frame = new JFrame();
        this.frame.setLayout(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(this.memberList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        JPanel toolPanel = new JPanel(new BorderLayout());
        toolPanel.add(this.addButton = new JButton("+"), BorderLayout.PAGE_START);
        this.addButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
        this.addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Object selection = JOptionPane.showInputDialog(frame,
                    "What is the new person's gender?",
                    "Select Gender",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    Gender.values(),
                    null);
                if (selection == null) {
                    return;
                }
                UUID id = familyTree.addPerson(new Person((Gender) selection));
                notifyChange();
                memberList.setSelectedValue(id, true);
                personEditor.setSubject(id);
                treeView.setSubject(id);
                personEditor.firstName.requestFocusInWindow();
            }
        });
        this.searchField = new JTextField();
        this.searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                UUID id = personEditor.getSubject();
                memberListModel.filter(searchField.getText());
                setSubject(id);
                updateWindow();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                UUID id = personEditor.getSubject();
                memberListModel.filter(searchField.getText());
                setSubject(id);
                updateWindow();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {}
        });
        toolPanel.add(this.addButton, BorderLayout.CENTER);
        toolPanel.add(this.searchField, BorderLayout.PAGE_END);
        toolPanel.setPreferredSize(new Dimension(250, 150));
        listPanel.add(toolPanel, BorderLayout.PAGE_START);
        listPanel.add(scrollPane, BorderLayout.CENTER);
        this.frame.add(listPanel, BorderLayout.LINE_START);
        JPanel personPanel = new JPanel();
        personPanel.setBackground(new Color(248, 248, 248));
        personPanel.setLayout(new BorderLayout());
        personPanel.setPreferredSize(new Dimension(700, 600));
        this.personEditor = new PersonEditor(this);
        this.treeView = new TreeView(this);
        this.memberList.addListSelectionListener(new PersonListSelectionListener(this));
        this.memberList.setSelectedIndex(0);
        personPanel.add(treeView, BorderLayout.CENTER);
        personPanel.add(this.personEditor, BorderLayout.PAGE_END);
        this.frame.add(personPanel, BorderLayout.CENTER);
        this.frame.pack();
        this.frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        this.frame.setLocationRelativeTo(null);
        this.frame.setResizable(true);
        new MainWindowListener(this);
        this.updateWindow();
        this.frame.setVisible(true);
        if (this.familyTree.size() == 0) {
            this.addButton.requestFocusInWindow();
        } else {
            this.memberList.requestFocusInWindow();
        }
    }

    private void setSubject(UUID id) {
        if (id != null && this.familyTree.getPerson(id).matches(this.searchField.getText())) {
            this.memberList.setSelectedValue(id, true);
        } else {
            this.memberList.clearSelection();
        }
        this.personEditor.setSubject(id);
        this.treeView.setSubject(id);
    }

    private void updateWindow() {
        if (this.changed) {
            this.frame.setTitle("*" + ((this.xml != null) ? this.xml.getName() : " New Family Tree"));
        } else {
            this.frame.setTitle((this.xml != null) ? this.xml.getName() : "New Family Tree");
        }
        this.memberListModel.update();
        this.memberList.setSelectedValue(this.personEditor.id, true);
        this.treeView.reload();
    }

    private void notifyChange() {
        this.changed = true;
        this.updateWindow();
    }
}
