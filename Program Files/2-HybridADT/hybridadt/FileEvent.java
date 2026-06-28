package hybridadt;

import java.io.Serializable;
import java.nio.file.Path;

/**
 * Immutable filesystem event passed to the ADT.
 */
public record FileEvent(
        EventType type,
        Path path
) implements Serializable {

    public enum EventType {
        CREATE,
        MODIFY,
        DELETE
    }
}
