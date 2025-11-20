package com.bone.engine.extension.support.expression;

import com.bone.engine.extension.api.spi.ExpressionEvaluator;
import com.bone.engine.extension.support.context.BizContext;

import java.util.function.Predicate;

public class AviatorExpressionEvaluator implements ExpressionEvaluator {
    @Override
    public boolean evaluate(String expression, BizContext<?> context, Object implementation) {
        return false;
    }

    @Override
    public Predicate<BizContext<?>> compile(String expression) {
        return null;
    }
}
