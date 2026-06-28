package searching;

import hybridadt.FileMetadata;
import hybridadt.HybridADTInterface;

import java.util.List;

/**
 * Handles filename and extension-based searches.
 */
public class FileSearch {

    private final HybridADTInterface adt;

    public FileSearch(HybridADTInterface adt) {
        this.adt = adt;
    }

    public List<FileMetadata> searchByNamePrefix(String prefix) {
        return adt.searchByFilenamePrefix(prefix);
    }

    public List<FileMetadata> filterByExtension(String extension) {
        return adt.filterByExtension(extension);
    }
}
