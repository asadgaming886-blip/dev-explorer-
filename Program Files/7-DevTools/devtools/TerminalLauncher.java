package devtools;

import hybridadt.HybridADTInterface;

/**
 * Launches system terminal at the current directory.
 */
public class TerminalLauncher {

    private final HybridADTInterface adt;

    public TerminalLauncher(HybridADTInterface adt) {
        this.adt = adt;
    }

    public void openHere() {
        adt.openTerminalHere();
    }
}
