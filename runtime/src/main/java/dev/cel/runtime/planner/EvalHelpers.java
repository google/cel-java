package dev.cel.runtime.planner;

import dev.cel.common.values.CelValue;
import dev.cel.common.values.ErrorValue;
import dev.cel.runtime.GlobalResolver;

final class EvalHelpers {

    static CelValue evalNonstrictly(CelValueInterpretable interpretable, GlobalResolver resolver) {
        try {
            return interpretable.eval(resolver);
        } catch (Exception e) {
            return ErrorValue.create(e);
        }
    }

    private EvalHelpers() {}
}
