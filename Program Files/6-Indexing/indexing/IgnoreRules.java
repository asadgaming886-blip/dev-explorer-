package indexing;

import hybridadt.HybridADTInterface;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages ignore rules for indexing.
 */
public class IgnoreRules {

    private final HybridADTInterface adt;
    private final List<String> rules = new ArrayList<>();

    public IgnoreRules(HybridADTInterface adt) {
        this.adt = adt;
    }

    public void addRule(String rule) {
        if (rule == null || rule.isBlank()) return;
        rules.add(rule.trim());
        adt.applyIgnoreRules(rules);
    }

    public void removeRule(String rule) {
        rules.remove(rule);
        adt.applyIgnoreRules(rules);
    }

    public void clearRules() {
        rules.clear();
        adt.applyIgnoreRules(rules);
    }

    public List<String> getRules() {
        return List.copyOf(rules);
    }
}
