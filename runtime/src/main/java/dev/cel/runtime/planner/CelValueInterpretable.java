package dev.cel.runtime.planner;

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.annotations.Internal;
import dev.cel.common.values.CelValue;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;
/**
 * Represent an expression which can be interpreted repeatedly using a given activation.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
interface CelValueInterpretable {

  CelValue eval(GlobalResolver resolver) throws CelEvaluationException;
}
