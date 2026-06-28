package system;

import hybridadt.HybridADTInterface;

/**
 * Represents the background indexing worker.
 * Actual indexing logic lives inside HybridADT.
 */
public class BackgroundIndexer implements Runnable {

    private final HybridADTInterface adt;
    private volatile boolean active = true;

    public BackgroundIndexer(HybridADTInterface adt) {
        this.adt = adt;
    }

    @Override
    public void run() {
        adt.startBackgroundIndexer();

        while (active) {
            try {
                Thread.sleep(500); // lightweight idle loop
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        adt.stopBackgroundIndexer();
    }

    public void shutdown() {
        active = false;
    }
}
