package indexing;

import hybridadt.FileEvent;
import hybridadt.HybridADTInterface;

/**
 * High-level controller for indexing operations.
 */
public class IndexManager {

    private final HybridADTInterface adt;

    public IndexManager(HybridADTInterface adt) {
        this.adt = adt;
    }

    public void buildIndex(String rootPath) {
        adt.buildInitialIndex(rootPath);
    }

    public void reindexDirectory(String path) {
        adt.reindexDirectory(path);
    }

    public void startBackgroundIndexing() {
        adt.startBackgroundIndexer();
    }

    public void stopBackgroundIndexing() {
        adt.stopBackgroundIndexer();
    }

    public void handleFileEvent(FileEvent event) {
        adt.handleFileSystemEvent(event);
    }

    public void shutdown() {
        adt.shutdown();
    }
}
