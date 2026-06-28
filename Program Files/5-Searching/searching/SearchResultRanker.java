package searching;

import hybridadt.FileMetadata;
import hybridadt.HybridADTInterface;

import java.util.List;

/**
 * Ranks search results using ADT-provided ranking logic.
 */
public class SearchResultRanker {

    private final HybridADTInterface adt;

    public SearchResultRanker(HybridADTInterface adt) {
        this.adt = adt;
    }

    public List<FileMetadata> rank(List<FileMetadata> results) {
        return adt.rankSearchResults(results);
    }
}
