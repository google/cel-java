package dev.cel.runtime;

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;
import dev.cel.common.values.CelValue;
/**
 * Represent an expression which can be interpreted repeatedly using a given activation.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
public interface CelValueInterpretable {

  CelValue eval(GlobalResolver resolver) throws CelEvaluationException;
}
