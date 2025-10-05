package com.bone.engine.extension.expression;

import com.bone.engine.extension.BizContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * ExpressionEvaluator
 *
 * @author renhui.trh 2023-11-9
 */
public class ExpressionEvaluator {

    public static boolean evaluateExpression(String expression, BizContext bizContext) {
        // 创建一个ExpressionParser实例
        ExpressionParser parser = new SpelExpressionParser();
        Expression spelExpression = parser.parseExpression(expression);
        return (boolean) spelExpression.getValue(bizContext);
    }


    public static void main(String[] args) {

        BizContext bizContext = new BizContext();
        bizContext.setBizCode("saasTpa");
        evaluateExpression("bizCode == 'saasTpa'", bizContext);
    }
}

