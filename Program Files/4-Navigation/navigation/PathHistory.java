package navigation;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Maintains navigation history for directory traversal.
 */
public class PathHistory {

    private final Deque<String> history = new ArrayDeque<>();

    public void push(String path) {
        if (path == null || path.isBlank()) return;
        if (!history.isEmpty() && history.peek().equals(path)) return;
        history.push(path);
    }

    public String pop() {
        return history.isEmpty() ? null : history.pop();
    }

    public String peek() {
        return history.peek();
    }

    public boolean canGoBack() {
        return history.size() > 1;
    }

    public void clear() {
        history.clear();
    }
}
