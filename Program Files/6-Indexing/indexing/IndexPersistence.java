package indexing;

import hybridadt.HybridADTInterface;

/**
 * Explicit persistence control for index state.
 */
public class IndexPersistence {

    private final HybridADTInterface adt;

    public IndexPersistence(HybridADTInterface adt) {
        this.adt = adt;
    }

    public void save() {
        adt.persistIndexToDisk();
    }

    public void load() {
        adt.loadIndexFromDisk();
    }
}
