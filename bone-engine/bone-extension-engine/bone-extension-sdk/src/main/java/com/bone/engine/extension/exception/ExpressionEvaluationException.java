package com.bone.engine.extension.exception;

/**
 * 扩展点表达式求值异常，表示在SpEL表达式计算过程中发生的错误
 * <p>
 * 当扩展点的条件表达式无法正确解析或执行时抛出此异常
 * 
 * @author bone team
 */
public class ExpressionEvaluationException extends ExtensionException {

    /**
     * 异常错误码常量
     */
    public static final String ERROR_CODE = "EXPRESSION_EVALUATION_ERROR";

    /**
     * 失败的表达式字符串
     */
    private final String expression;

    /**
     * 创建表达式求值异常
     * 
     * @param expression 失败的表达式字符串
     * @param message 详细错误信息
     */
    public ExpressionEvaluationException(String expression, String message) {
        super(ERROR_CODE, String.format(
                "Failed to evaluate expression '%s': %s",
                expression, message
        ));
        this.expression = expression;
    }

    /**
     * 创建表达式求值异常
     * 
     * @param expression 失败的表达式字符串
     * @param cause 根本原因异常
     */
    public ExpressionEvaluationException(String expression, Throwable cause) {
        super(ERROR_CODE, String.format(
                "Failed to evaluate expression '%s'",
                expression
        ), cause);
        this.expression = expression;
    }

    /**
     * 创建表达式求值异常
     * 
     * @param expression 失败的表达式字符串
     * @param message 详细错误信息
     * @param cause 根本原因异常
     */
    public ExpressionEvaluationException(String expression, String message, Throwable cause) {
        super(ERROR_CODE, String.format(
                "Failed to evaluate expression '%s': %s",
                expression, message
        ), cause);
        this.expression = expression;
    }

    /**
     * 获取失败的表达式字符串
     * 
     * @return 表达式字符串
     */
    public String getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return "ExpressionEvaluationException{" +
                "errorCode='" + ERROR_CODE + "'" +
                ", expression='" + expression + "'" +
                ", message='" + getMessage() + "'" +
                '}';
    }
}