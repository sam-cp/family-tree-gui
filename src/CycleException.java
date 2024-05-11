import java.util.UUID;

public class CycleException extends RuntimeException {
    private UUID[] ids;

    public CycleException(UUID[] ids) {
        super("A cyclic ancestry was detected in the family tree (i.e., someone is their own ancestor/descendant).");
        this.ids = ids.clone();
    }

    public UUID[] getIDs() {
        return ids.clone();
    } 
}