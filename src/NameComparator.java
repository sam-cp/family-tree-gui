import java.util.Comparator;
import java.util.UUID;

public class NameComparator implements Comparator<UUID> {
    FamilyTree familyTree;

    public NameComparator(FamilyTree familyTree) {
        this.familyTree = familyTree;
    }

    @Override
    public int compare(UUID o1, UUID o2) {
        Person p1 = familyTree.getPerson(o1);
        Person p2 = familyTree.getPerson(o2);
        String lName1 = p1.lastName;
        String lName2 = p2.lastName;
        if (lName1 == null || lName2 == null) {
            return ((lName1 == null) ? -1 : 0) + ((lName2 == null) ? 1 : 0);
        }
        if (lName1.compareToIgnoreCase(lName2) != 0) {
            return lName1.compareToIgnoreCase(lName2);
        }
        String fName1 = p1.getPersonalName();
        String fName2 = p2.getPersonalName();
        if (fName1 == null) {
            fName1 = "";
        }
        if (fName2 == null) {
            fName2 = "";
        }
        return fName1.compareToIgnoreCase(fName2);
    }
}
