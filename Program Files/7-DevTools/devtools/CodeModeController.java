package devtools;

import hybridadt.HybridADTInterface;

/**
 * Controls developer-oriented "code mode".
 */
public class CodeModeController {

    private final HybridADTInterface adt;
    private boolean enabled = false;

    public CodeModeController(HybridADTInterface adt) {
        this.adt = adt;
    }

    public void enable() {
        enabled = true;
        adt.toggleCodeMode(true);
    }

    public void disable() {
        enabled = false;
        adt.toggleCodeMode(false);
    }

    public void toggle() {
        enabled = !enabled;
        adt.toggleCodeMode(enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }
}
