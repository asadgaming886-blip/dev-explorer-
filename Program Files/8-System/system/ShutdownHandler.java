package system;

import hybridadt.HybridADTInterface;

/**
 * Ensures clean shutdown of the application.
 */
public class ShutdownHandler {

    private final HybridADTInterface adt;
    private final ThreadCoordinator coordinator;

    public ShutdownHandler(HybridADTInterface adt, ThreadCoordinator coordinator) {
        this.adt = adt;
        this.coordinator = coordinator;
    }

    public void shutdown() {
        coordinator.stop();
        adt.shutdown();
    }
}
