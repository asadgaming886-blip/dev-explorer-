package devtools;

import hybridadt.HybridADTInterface;

/**
 * Path helper utilities backed by the ADT.
 */
public class PathUtility {

    private final HybridADTInterface adt;

    public PathUtility(HybridADTInterface adt) {
        this.adt = adt;
    }

    public String absolutePathOf(String fileName) {
        return adt.getAbsolutePath(fileName);
    }

    public String relativePathOf(String fileName) {
        return adt.getRelativePath(fileName);
    }

    public String currentDirectory() {
        return adt.getCurrentPath();
    }
}
