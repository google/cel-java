package dev.cel.runtime.planner;

import static dev.cel.runtime.planner.EvalHelpers.evalNonstrictly;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import dev.cel.common.values.BoolValue;
import dev.cel.common.values.CelValue;
import dev.cel.common.values.ErrorValue;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;


final class EvalAnd implements CelValueInterpretable {

    private final ImmutableList<CelValueInterpretable> args;

    @Override
    public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
        ErrorValue errorValue = null;
        for (CelValueInterpretable arg : args) {
            CelValue argVal = evalNonstrictly(arg, resolver);
            if (argVal instanceof BoolValue) {
                // Short-circuit on false
                if (!((boolean) argVal.value())) {
                    return argVal;
                }
            } else if (argVal instanceof ErrorValue) {
                errorValue = (ErrorValue) argVal;
            }
        }

        if (errorValue != null) {
            return errorValue;
        }

        return BoolValue.create(true);
    }

    static EvalAnd create(ImmutableList<CelValueInterpretable> args) {
        return new EvalAnd(args);
    }

    private EvalAnd(ImmutableList<CelValueInterpretable> args) {
        Preconditions.checkArgument(args.size() == 2);
        this.args = args;
    }
}