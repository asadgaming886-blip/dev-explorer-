package navigation;

import hybridadt.HybridADTInterface;
import hybridadt.FileMetadata;

import java.util.List;

/**
 * Handles directory navigation using HybridADT.
 */
public class NavigationController {

    private final HybridADTInterface adt;
    private final PathHistory pathHistory;

    public NavigationController(HybridADTInterface adt) {
        this.adt = adt;
        this.pathHistory = new PathHistory();
        pathHistory.push(adt.getCurrentPath());
    }

    public void openDirectory(String path) {
        adt.openDirectory(path);
        pathHistory.push(path);
    }

    public void goBack() {
        if (!pathHistory.canGoBack()) return;
        pathHistory.pop(); // remove current
        String previous = pathHistory.peek();
        if (previous != null) {
            adt.navigateToPath(previous);
        }
    }

    public String getCurrentPath() {
        return adt.getCurrentPath();
    }

    public List<FileMetadata> listCurrentDirectory() {
        return adt.listCurrentDirectory();
    }

    public void refresh() {
        adt.refreshCurrentDirectory();
    }

    public void resetToRoot(String rootPath) {
        pathHistory.clear();
        adt.openDirectory(rootPath);
        pathHistory.push(rootPath);
    }
}
