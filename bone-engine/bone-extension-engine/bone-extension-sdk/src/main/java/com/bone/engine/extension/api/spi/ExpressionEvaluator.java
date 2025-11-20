package com.bone.engine.extension.api.spi;

import com.bone.engine.extension.support.context.BizContext;

import java.util.function.Predicate;

// com.bone.extension.api.spi.SpELExpressionEvaluator
public interface ExpressionEvaluator {
    boolean evaluate(String expression, BizContext<?> context, Object implementation);
    Predicate<BizContext<?>> compile(String expression);
}