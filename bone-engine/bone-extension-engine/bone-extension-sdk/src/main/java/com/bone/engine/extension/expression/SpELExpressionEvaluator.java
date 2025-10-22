package com.bone.engine.extension.expression;

import com.bone.engine.extension.context.BizContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class SpELExpressionEvaluator {
    
    private static final Logger log = LoggerFactory.getLogger(SpELExpressionEvaluator.class);
    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();
    
    public Object evaluate(String expressionString, Object context) {
        if (!StringUtils.hasText(expressionString) || context == null) {
            return null;
        }
        
        try {
            // 从缓存获取表达式
            Expression expression = expressionCache.computeIfAbsent(expressionString, parser::parseExpression);
            
            // 创建评估上下文
            EvaluationContext evaluationContext = createEvaluationContext(context);
            
            // 评估表达式
            return expression.getValue(evaluationContext);
        } catch (Exception e) {
            log.error("Failed to evaluate expression: {}", expressionString, e);
            return null;
        }
    }
    
    private EvaluationContext createEvaluationContext(Object context) {
        StandardEvaluationContext evaluationContext = new StandardEvaluationContext();
        evaluationContext.setRootObject(context);
        
        // 如果上下文是BizContext类型，添加其属性
        if (context instanceof BizContext) {
            BizContext<?> bizContext = (BizContext<?>) context;
            evaluationContext.setVariable("tenantCode", bizContext.getTenantCode());
            evaluationContext.setVariable("bizCode", bizContext.getBizCode());
            evaluationContext.setVariable("useCase", bizContext.getUseCase());
            evaluationContext.setVariable("scenario", bizContext.getScenario());
            // 暂时注释掉data访问，避免编译错误
            // evaluationContext.setVariable("data", bizContext.getData());
            evaluationContext.setVariable("timestamp", System.currentTimeMillis());
        }
        
        return evaluationContext;
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