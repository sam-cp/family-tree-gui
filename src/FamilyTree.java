import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class FamilyTree {
    private static final String SCHEMA_PATH = "/familytree.xsd";
    private static final String NAMESPACE = "https://familytree.samprewett.com/familytree.xsd";
    private static Schema schema = null;
    public static void createSchema() throws SAXException {
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        FamilyTree.schema = sf.newSchema(new StreamSource(FamilyTree.class.getResourceAsStream(SCHEMA_PATH)));
    }

    private class FamilyTreeErrorHandler implements ErrorHandler {
        ArrayList<SAXParseException> errorList, warningList;

        public FamilyTreeErrorHandler(ArrayList<SAXParseException> errorList, ArrayList<SAXParseException> warningList) {
            this.errorList = errorList;
            this.warningList = warningList;
        }

        public void error(SAXParseException e) {
            errorList.add(e);
        }

        public void fatalError(SAXParseException e) {
            throw new InvalidFormatException(e.getMessage());
        }

        public void warning(SAXParseException e) {
            this.warningList.add(e);
        }
    }

    private class PersonNode implements Comparable<PersonNode> {
        public UUID id;
        public Person person;
        public PersonNode father;
        public PersonNode mother;
        public PersonNode spouse;
        public TreeSet<PersonNode> children;

        public PersonNode(Person p) {
            this(p, UUID.randomUUID());
        }

        public PersonNode(Person p, UUID id) {
            this.id = id;
            this.person = p;
            this.father = this.mother = this.spouse = null;
            this.children = new TreeSet<PersonNode>();
        }

        @Override
        public int compareTo(FamilyTree.PersonNode o) {
            return this.id.compareTo(o.id);
        }
    }

    private TreeMap<UUID, PersonNode> members;

    private void _setFather(PersonNode ofNode, PersonNode toNode) {
        ofNode.father = toNode;
        toNode.children.add(ofNode);
    }

    private void _setMother(PersonNode ofNode, PersonNode toNode) {
        ofNode.mother = toNode;
        toNode.children.add(ofNode);
    }

    private void _setSpouse(PersonNode ofNode, PersonNode toNode) {
        ofNode.spouse = toNode;
        toNode.spouse = ofNode;
    }

    private boolean _isDescendant(PersonNode pNode, PersonNode ofNode) {
        if (pNode == null || pNode == ofNode) {
            return false;
        }
        if (pNode.father == ofNode || pNode.mother == ofNode) {
            return true;
        }
        return this._isDescendant(pNode.father, ofNode) || this._isDescendant(pNode.mother, ofNode);
    }

    private class InternalCycleException extends Exception {
        public ArrayList<UUID> uuids;

        public InternalCycleException(UUID source) {
            super();
            uuids = new ArrayList<UUID>();
            uuids.add(source);
        }

        public void finishAndThrow() {
            UUID arr[] = new UUID[uuids.size()];
            throw new CycleException(this.uuids.toArray(arr));
        }
    }

    private void _checkForCycles() {
        TreeMap<UUID, Boolean> blackNodes = new TreeMap<UUID, Boolean>();  // gray if false, black if true, white if not present
        for (Map.Entry<UUID, PersonNode> entry : this.members.entrySet()) {
            if (blackNodes.containsKey(entry.getKey())) {
                continue;
            }
            try {
                this._dfsHelper(entry.getValue(), blackNodes);
            } catch (InternalCycleException e) {
                e.uuids.add(entry.getKey());
                e.finishAndThrow();
            }
        }
    }

    private void _dfsHelper(PersonNode node, TreeMap<UUID, Boolean> blackNodes) throws InternalCycleException {
        blackNodes.put(node.id, false);
        if (node.father != null) {
            if (blackNodes.containsKey(node.father.id)) {
                if (!blackNodes.get(node.father.id).booleanValue()) {
                    throw new InternalCycleException(node.father.id);
                }
            } else {
                try {
                    this._dfsHelper(node.father, blackNodes);
                } catch (InternalCycleException e) {
                    e.uuids.add(node.father.id);
                    if (e.uuids.get(0) == node.father.id) {
                        e.finishAndThrow();
                    }
                }
            }
        }
        if (node.mother != null) {
            if (blackNodes.containsKey(node.mother.id)) {
                if (!blackNodes.get(node.mother.id).booleanValue()) {
                    throw new InternalCycleException(node.mother.id);
                }
            } else {
                try {
                    this._dfsHelper(node.mother, blackNodes);
                } catch (InternalCycleException e) {
                    e.uuids.add(node.mother.id);
                    if (e.uuids.get(0) == node.mother.id) {
                        e.finishAndThrow();
                    }
                    throw e;
                }
            }
        }
        blackNodes.put(node.id, true);
    }

    public FamilyTree() {
        this.members = new TreeMap<UUID, PersonNode>();
    }

    public FamilyTree(File xml) throws ParserConfigurationException, SAXException, IOException {
        this();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setSchema(FamilyTree.schema);
        DocumentBuilder db = dbf.newDocumentBuilder();
        ArrayList<SAXParseException> errorList = new ArrayList<SAXParseException>();
        ArrayList<SAXParseException> warningList = new ArrayList<SAXParseException>();
        db.setErrorHandler(new FamilyTreeErrorHandler(errorList, warningList));
        Document document = db.parse(xml);
        if (!errorList.isEmpty()) {
            String error = "";
            for (SAXParseException e : errorList) {
                error += e.getMessage() + "\n";
            }
            throw new InvalidFormatException(error.strip());
        }
        NodeList nodes = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element elem = (Element) n;
            Person p = null;
            if (n.getLocalName() == "female") {
                p = new Person(Gender.FEMALE);
            } else {
                p = new Person(Gender.MALE);
            }
            Element name = (Element) elem.getElementsByTagName("name").item(0);
            Element firstname, middlename, lastname, preferredname, maidenname, notes;
            firstname = (Element) name.getElementsByTagName("firstname").item(0);
            middlename = (Element) name.getElementsByTagName("middlename").item(0);
            lastname = (Element) name.getElementsByTagName("lastname").item(0);
            preferredname = (Element) name.getElementsByTagName("preferredname").item(0);
            maidenname = (Element) name.getElementsByTagName("maidenname").item(0);
            notes = (Element) elem.getElementsByTagName("notes").item(0);
            if (firstname != null) {
                p.firstName = firstname.getTextContent();
            }
            if (middlename != null) {
                p.middleName = middlename.getTextContent();
            }
            p.lastName = lastname.getTextContent();
            if (preferredname != null) {
                p.nickname = preferredname.getTextContent();
            }
            if (maidenname != null) {
                p.maidenName = maidenname.getTextContent();
            }
            if (notes != null) {
                p.notes = notes.getTextContent();
            }
            UUID uuid = UUID.fromString(elem.getAttribute("uuid"));
            this.members.put(uuid, new PersonNode(p, uuid));
        }
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element elem = (Element) n;
            UUID uuid = UUID.fromString(elem.getAttribute("uuid"));
            NodeList family = ((Element) elem.getElementsByTagName("family").item(0)).getChildNodes();
            for (int j = 0; j < family.getLength(); j++) {
                Node fn = family.item(j);
                if (fn.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element felem = (Element) fn;
                if (felem.getLocalName() == "father") {
                    UUID fUuid = UUID.fromString(felem.getAttribute("uuid"));
                    this._setFather(this.members.get(uuid), this.members.get(fUuid));
                } else if (felem.getLocalName() == "mother") {
                    UUID fUuid = UUID.fromString(felem.getAttribute("uuid"));
                    this._setMother(this.members.get(uuid), this.members.get(fUuid));
                } else if (felem.getLocalName() == "husband") {
                    UUID fUuid = UUID.fromString(felem.getAttribute("uuid"));
                    this._setSpouse(this.members.get(uuid), this.members.get(fUuid));
                }
            }
        }
        this._checkForCycles();
    }

    public int size() {
        return this.members.size();
    }

    public TreeSet<UUID> getMembers() {
        return new TreeSet<UUID>(this.members.keySet());
    }

    public UUID addPerson(Person p) {
        PersonNode node = new PersonNode(p);
        this.members.put(node.id, node);
        return node.id;
    }

    public boolean removePerson(UUID id) {
        PersonNode node = this.members.get(id);
        if (node == null) {
            return false;
        }
        if (node.father != null) {
            node.father.children.remove(node);
        }
        if (node.mother != null) {
            node.mother.children.remove(node);
        }
        if (node.spouse != null) {
            node.spouse.spouse = null;
        }
        for (PersonNode n : node.children) {
            switch (node.person.getGender()) {
            case MALE:
                n.father = null;
                break;
            case FEMALE:
                n.mother = null;
                break;
            }
        }
        members.remove(id);
        return true;
    }

    public boolean hasPerson(UUID id) {
        return this.members.containsKey(id);
    }

    public Person getPerson(UUID id) {
        PersonNode node = this.members.get(id);
        if (node == null) {
            return null;
        }
        return node.person;
    }

    public UUID getFather(UUID id) {
        PersonNode node = this.members.get(id);
        if (node == null || node.father == null) {
            return null;
        }
        return node.father.id;
    }

    public UUID getMother(UUID id) {
        PersonNode node = this.members.get(id);
        if (node == null || node.mother == null) {
            return null;
        }
        return node.mother.id;
    }

    public UUID getSpouse(UUID id) {
        PersonNode node = this.members.get(id);
        if (node == null || node.spouse == null) {
            return null;
        }
        return node.spouse.id;
    }

    public TreeSet<UUID> getChildren(UUID id) {
        PersonNode node = this.members.get(id);
        if (node == null) {
            return null;
        }
        TreeSet<UUID> children = new TreeSet<UUID>();
        for (PersonNode child : node.children) {
            children.add(child.id);
        }
        return children;
    }

    public boolean removeFather(UUID id) {
        PersonNode child = this.members.get(id);
        if (child == null) {
            return false;
        }
        if (child.father != null) {
            child.father.children.remove(child);
            child.father = null;
        }
        return true;
    }

    public boolean removeMother(UUID id) {
        PersonNode child = this.members.get(id);
        if (child == null) {
            return false;
        }
        if (child.mother != null) {
            child.mother.children.remove(child);
            child.mother = null;
        }
        return true;
    }

    public boolean removeSpouse(UUID id) {
        PersonNode node = this.members.get(id);
        if (node == null) {
            return false;
        }
        if (node.spouse != null) {
            node.spouse.spouse = null;
            node.spouse = null;
        }
        return true;
    }

    public boolean setFather(UUID of, UUID to) {
        if (of.compareTo(to) == 0) {
            return false;
        }
        PersonNode child = this.members.get(of);
        PersonNode parent = this.members.get(to);
        if (child == null || parent == null || parent.person.getGender() == Gender.FEMALE) {
            return false;
        }
        if (this._isDescendant(parent, child)) {
            return false;
        }
        this.removeFather(of);
        this._setFather(child, parent);
        return true;
    }

    public boolean setMother(UUID of, UUID to) {
        if (of.compareTo(to) == 0) {
            return false;
        }
        PersonNode child = this.members.get(of);
        PersonNode parent = this.members.get(to);
        if (child == null || parent == null || parent.person.getGender() == Gender.MALE) {
            return false;
        }
        if (this._isDescendant(parent, child)) {
            return false;
        }
        this.removeMother(of);
        this._setMother(child, parent);
        return true;
    }

    public boolean setSpouse(UUID of, UUID to) {
        PersonNode ofNode = this.members.get(of);
        PersonNode toNode = this.members.get(to);
        if (ofNode == null || toNode == null || ofNode.person.getGender() == toNode.person.getGender()) {
            return false;
        }
        this.removeSpouse(of);
        this.removeSpouse(to);
        this._setSpouse(ofNode, toNode);
        return true;
    }

    public boolean isDescendant(UUID id, UUID of) {
        PersonNode ofNode = this.members.get(of);
        if (ofNode == null) {
            return false;
        }
        return this._isDescendant(this.members.get(id), ofNode);
    }

    public String getName(UUID id) {
        PersonNode pNode = members.get(id);
        if (pNode == null) {
            return null;
        }
        Person p = pNode.person;
        String name = p.lastName;
        if (name == null) {
            return null;
        }
        String personalName = p.getPersonalName();
        if (personalName != null) {
            name = personalName + " " + name;
        } else {
            switch(p.getGender()) {
            case MALE:
                name = "Mr. " + name;
                break;
            case FEMALE:
                if (pNode.spouse != null) {
                    name = "Mrs. " + name;
                } else {
                    name = "Ms. " + name;
                }
                break;
            }
        }
        return name;
    }

    public void checkSave() throws InvalidFamilyTreeException {
        for (Map.Entry<UUID, PersonNode> entry : this.members.entrySet()) {
            if (entry.getValue().person.lastName == null) {
                throw new InvalidFamilyTreeException("One or more of your members does not have a last name.");
            }
        }
    }

    public void saveToFile(File xml) throws ParserConfigurationException, FileNotFoundException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.newDocument();
        Element familyTree = document.createElementNS(NAMESPACE, "familytree");
        for (Map.Entry<UUID, PersonNode> entry : this.members.entrySet()) {
            Element personElement = null;
            PersonNode pNode = entry.getValue();
            Person p = pNode.person;
            switch (p.getGender()) {
            case MALE:
                personElement = document.createElementNS(NAMESPACE, "male");
                break;
            case FEMALE:
                personElement = document.createElementNS(NAMESPACE, "female");
                break;
            }
            personElement.setAttribute("uuid", entry.getKey().toString());
            Element nameElement = document.createElementNS(NAMESPACE, "name");
            if (p.firstName != null) {
                Element e = document.createElementNS(NAMESPACE, "firstname");
                e.appendChild(document.createTextNode(p.firstName));
                nameElement.appendChild(e);
            }
            if (p.middleName != null) {
                Element e = document.createElementNS(NAMESPACE, "middlename");
                e.appendChild(document.createTextNode(p.middleName));
                nameElement.appendChild(e);
            }
            Element lastNameElement = document.createElementNS(NAMESPACE, "lastname");
            lastNameElement.appendChild(document.createTextNode(p.lastName));
            nameElement.appendChild(lastNameElement);
            if (p.nickname != null) {
                Element e = document.createElementNS(NAMESPACE, "preferredname");
                e.appendChild(document.createTextNode(p.nickname));
                nameElement.appendChild(e);
            }
            if (p.maidenName != null) {
                Element e = document.createElementNS(NAMESPACE, "maidenname");
                e.appendChild(document.createTextNode(p.maidenName));
                nameElement.appendChild(e);
            }
            personElement.appendChild(nameElement);

            Element familyElement = document.createElementNS(NAMESPACE, "family");
            if (pNode.father != null) {
                Element e = document.createElementNS(NAMESPACE, "father");
                e.setAttribute("uuid", pNode.father.id.toString());
                familyElement.appendChild(e);
            }
            if (pNode.mother != null) {
                Element e = document.createElementNS(NAMESPACE, "mother");
                e.setAttribute("uuid", pNode.mother.id.toString());
                familyElement.appendChild(e);
            }
            if (p.getGender() == Gender.FEMALE && pNode.spouse != null) {
                Element e = document.createElementNS(NAMESPACE, "husband");
                e.setAttribute("uuid", pNode.spouse.id.toString());
                familyElement.appendChild(e);
            }
            personElement.appendChild(familyElement);

            if (p.notes != null) {
                Element notesElement = document.createElementNS(NAMESPACE, "notes");
                notesElement.appendChild(document.createTextNode(p.notes));
                personElement.appendChild(notesElement);
            }
            familyTree.appendChild(personElement);
        }
        document.appendChild(familyTree);
        document.setXmlVersion("1.0");
        DOMImplementationLS ls = (DOMImplementationLS) db.getDOMImplementation();
        LSSerializer lss = ls.createLSSerializer();
        lss.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);
        LSOutput lso = ls.createLSOutput();
        lso.setByteStream(new FileOutputStream(xml, false));
        lss.write(document, lso);
    }
}
