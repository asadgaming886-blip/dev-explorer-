package hybridadt;

import java.io.Serializable;

/**
 * Immutable metadata snapshot for a file or directory.
 */
public record FileMetadata(
        String name,
        String path,
        String extension,
        long size,
        long lastModified,
        boolean isDirectory
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
