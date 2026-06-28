package searching;

import hybridadt.FileMetadata;
import hybridadt.HybridADTInterface;

import java.util.List;

/**
 * Handles content-based search queries.
 */
public class ContentSearch {

    private final HybridADTInterface adt;

    public ContentSearch(HybridADTInterface adt) {
        this.adt = adt;
    }

    public List<FileMetadata> search(String query) {
        return adt.searchByFileContent(query);
    }

    public List<FileMetadata> searchInCurrentDirectory(String query) {
        return adt.searchInCurrentDirectory(query);
    }
}
