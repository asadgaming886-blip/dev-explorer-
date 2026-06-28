package system;

import hybridadt.HybridADTInterface;

/**
 * Coordinates background threads used by the system.
 */
public class ThreadCoordinator {

    private final HybridADTInterface adt;
    private boolean running = false;

    public ThreadCoordinator(HybridADTInterface adt) {
        this.adt = adt;
    }

    public void start() {
        if (running) return;
        running = true;
        adt.startBackgroundIndexer();
    }

    public void stop() {
        if (!running) return;
        running = false;
        adt.stopBackgroundIndexer();
    }

    public boolean isRunning() {
        return running;
    }
}
