package dev.cel.common;

import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.CelCodePointArray;
import java.util.Optional;

/**
 * Common interface definition for source contents.
 *
 * <p>CEL Library Internals. Do Not Use. Consumers should instead use the canonical
 * implementations such as CelSource.
 */
@Internal
public interface Source {

  CelCodePointArray content();

  String description();

  Optional<String> getSnippet(int line);
}
