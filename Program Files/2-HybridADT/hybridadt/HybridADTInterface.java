package hybridadt;

import java.nio.file.Path;
import java.util.List;

/**
 * ADT contract for the Developer-Oriented File Explorer.
 * All modules interact ONLY through this interface.
 */
public interface HybridADTInterface {

    /* =========================
       Navigation Operations (6)
       ========================= */

    void openDirectory(String path);
    void navigateToParent();
    void navigateToPath(String path);
    String getCurrentPath();
    List<FileMetadata> listCurrentDirectory();
    void refreshCurrentDirectory();

    /* =========================
       Search Operations (6)
       ========================= */

    List<FileMetadata> searchByFilenamePrefix(String prefix);
    List<FileMetadata> searchByFileContent(String query);
    List<FileMetadata> searchInCurrentDirectory(String query);
    List<FileMetadata> filterByExtension(String extension);
    List<FileMetadata> rankSearchResults(List<FileMetadata> results);
    void clearSearch();

    /* =========================
       Indexing Operations (7)
       ========================= */

    void buildInitialIndex(String rootPath);
    void updateIndexOnFileCreate(String filePath);
    void updateIndexOnFileModify(String filePath);
    void updateIndexOnFileDelete(String filePath);
    void reindexDirectory(String path);
    void loadIndexFromDisk();
    void persistIndexToDisk();

    /* =========================
       Developer Utility Operations (5)
       ========================= */

    void openTerminalHere();
    String getAbsolutePath(String fileName);
    String getRelativePath(String fileName);
    void toggleCodeMode(boolean enabled);
    void applyIgnoreRules(List<String> ignorePatterns);

    /* =========================
       System & Concurrency Control (4)
       ========================= */

    void startBackgroundIndexer();
    void stopBackgroundIndexer();
    void handleFileSystemEvent(FileEvent event);
    void shutdown();
}
