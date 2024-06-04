package dev.cel.common;

import dev.cel.common.annotations.Internal;
import java.util.Optional;

/**
 * Common interface definition for source contents.
 *
 * <p>CEL Library Internals. Do Not Use. Consumers should instead use the canonical
 * implementations such as CelSource.
 */
@Internal
public interface Source {

  String description();

  Optional<String> getSnippet(int line);
}
