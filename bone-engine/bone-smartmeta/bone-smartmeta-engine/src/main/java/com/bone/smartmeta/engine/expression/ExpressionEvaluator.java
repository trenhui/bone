package com.bone.smartmeta.engine.expression;

import com.bone.smartmeta.engine.ExpressionEngine;

import java.util.Map;

/**
 * 表达式求值器接口
 */
public interface ExpressionEvaluator {
    
    /**
     * 评估表达式并返回结果
     * @param expression 表达式字符串
     * @param context 上下文变量
     * @return 表达式计算结果
     * @throws IllegalArgumentException 当表达式为空时抛出
     * @throws ExpressionEngine.ExpressionEvaluationException 当表达式求值出错时抛出
     */
    Object eval(String expression, Map<String, Object> context);
}