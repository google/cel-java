package dev.cel.runtime.planner;

import static dev.cel.runtime.planner.EvalHelpers.evalNonstrictly;

import com.google.common.base.Preconditions;
import dev.cel.common.values.BoolValue;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.ErrorValue;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;


final class EvalOr implements CelValueInterpretable {

    @SuppressWarnings("Immutable")
    private final CelValueInterpretable[] args;

    @Override
    public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
        ErrorValue errorValue = null;
        for (CelValueInterpretable arg : args) {
            CelValue argVal = evalNonstrictly(arg, resolver);
            if (argVal instanceof BoolValue) {
                // Short-circuit on true
                if (((boolean) argVal.value())) {
                    return argVal;
                }
            } else if (argVal instanceof ErrorValue) {
                errorValue = (ErrorValue) argVal;
            }
        }

        if (errorValue != null) {
            return errorValue;
        }

        return BoolValue.create(false);
    }

    static EvalOr create(CelValueInterpretable[] args) {
        return new EvalOr(args);
    }

    private EvalOr(CelValueInterpretable[] args) {
        Preconditions.checkArgument(args.length == 2);
        this.args = args;
    }
}