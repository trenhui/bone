package com.bone.engine.extension.expression;

import com.bone.engine.extension.BizContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring Expression Language (SpEL) 表达式评估器
 * 用于动态评估扩展点的路由条件表达式
 */
@Slf4j
public class SpELExpressionEvaluator implements ExpressionEvaluator {
    
    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();
    
    @Override
    public boolean evaluate(String expressionString, BizContext<?> context) {
        if (!StringUtils.hasText(expressionString) || context == null) {
            return false;
        }
        
        try {
            // 从缓存获取表达式
            Expression expression = expressionCache.computeIfAbsent(expressionString, parser::parseExpression);
            
            // 创建评估上下文
            EvaluationContext evaluationContext = createContext(context);
            
            // 评估表达式
            Boolean result = expression.getValue(evaluationContext, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            log.warn("Failed to evaluate expression: '{}' for context: {}, error: {}", 
                    expressionString, context, e.getMessage());
            return false;
        }
    }
    
    /**
     * 创建SpEL评估上下文
     */
    private EvaluationContext createContext(BizContext<?> context) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // 根对象为业务上下文
        context.setRootObject(context);
        
        // 添加上下文属性到评估上下文
        context.setVariable("tenantCode", context.getTenantCode());
        context.setVariable("bizCode", context.getBizCode());
        context.setVariable("useCase", context.getUseCase());
        context.setVariable("scenario", context.getScenario());
        context.setVariable("data", context.getData());
        context.setVariable("timestamp", context.getTimestamp());
        
        // 添加自定义属性
        if (context.getAttributes() != null) {
            for (Map.Entry<String, Object> entry : context.getAttributes().entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
        }
        
        return context;
    }
    
    /**
     * 清除表达式缓存
     */
    public void clearCache() {
        expressionCache.clear();
        log.info("Expression cache cleared");
    }
    
    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return expressionCache.size();
    }
}