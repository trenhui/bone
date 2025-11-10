package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.model.BusinessRule;
import com.bone.smartmeta.engine.model.RuleExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 默认业务规则引擎实现
 * 采用单例模式设计，确保全局只有一个规则引擎实例
 * 使用线程安全的集合和原子操作保证并发安全
 */
@Component
public class DefaultBusinessRuleEngine implements BusinessRuleEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBusinessRuleEngine.class);
    
    // 线程安全的单例实例（双重检查锁定模式）
    private static volatile DefaultBusinessRuleEngine instance;
    
    // 使用AtomicBoolean确保初始化的线程安全
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    
    // 性能统计指标
    private final AtomicLong totalExecutions = new AtomicLong(0);
    private final AtomicLong successfulExecutions = new AtomicLong(0);
    private final AtomicLong failedExecutions = new AtomicLong(0);
    private final AtomicLong executionTime = new AtomicLong(0);
    
    // 注入表达式引擎
    private final ExpressionEngine expressionEngine;
    
    // 规则缓存，提高性能
    private final Map<String, BusinessRule> ruleCache = new ConcurrentHashMap<>();
    
    // 私有构造函数，防止外部实例化
    @Autowired
    private DefaultBusinessRuleEngine(ExpressionEngine expressionEngine) {
        this.expressionEngine = expressionEngine;
        if (initialized.compareAndSet(false, true)) {
            LOGGER.info("BusinessRuleEngine initialized successfully");
        }
    }
    
    /**
     * 获取单例实例
     * @return DefaultBusinessRuleEngine实例
     */
    public static DefaultBusinessRuleEngine getInstance() {
        if (instance == null) {
            synchronized (DefaultBusinessRuleEngine.class) {
                if (instance == null) {
                    // 注意：在Spring环境中，应该通过Spring容器获取实例
                    // 这里仅作为备用方案，避免在非Spring环境中使用时出现问题
                    LOGGER.warn("Getting BusinessRuleEngine instance outside Spring context, creating new instance");
                    instance = new DefaultBusinessRuleEngine(new ExpressionEngine());
                }
            }
        }
        return instance;
    }
    
    @Override
    public RuleExecutionResult executeRule(BusinessRule rule, Map<String, Object> context) {
        long startTime = System.currentTimeMillis();
        totalExecutions.incrementAndGet();
        
        RuleExecutionResult result = new RuleExecutionResult();
        result.setRuleId(rule.getRuleId());
        result.setRuleName(rule.getRuleName());
        
        try {
            // 验证规则条件
            boolean conditionMet = evaluateCondition(rule.getCondition(), context);
            result.setConditionMet(conditionMet);
            
            // 如果条件满足，执行规则动作
            if (conditionMet) {
                executeActions(rule, context, result);
                successfulExecutions.incrementAndGet();
                result.setSuccess(true);
                LOGGER.debug("Rule executed successfully: {}", rule.getRuleId());
            } else {
                // 条件不满足，规则未执行
                result.setSuccess(true);
                result.setMessage("Condition not met, rule not executed");
                LOGGER.debug("Rule condition not met, rule not executed: {}", rule.getRuleId());
            }
        } catch (Exception e) {
            failedExecutions.incrementAndGet();
            result.setSuccess(false);
            result.setErrorMessage("Rule execution failed: " + e.getMessage());
            LOGGER.error("Failed to execute rule: {}", rule.getRuleId(), e);
        } finally {
            // 计算执行时间
            long endTime = System.currentTimeMillis();
            long timeSpent = endTime - startTime;
            executionTime.addAndGet(timeSpent);
            result.setExecutionTime(timeSpent);
        }
        
        return result;
    }
    
    @Override
    public RuleExecutionResult executeRuleById(String ruleId, Map<String, Object> context) {
        // 从缓存中获取规则，如果没有则抛出异常
        BusinessRule rule = ruleCache.get(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("Rule not found: " + ruleId);
        }
        
        return executeRule(rule, context);
    }
    
    @Override
    public void registerRule(BusinessRule rule) {
        if (rule == null || rule.getRuleId() == null) {
            throw new IllegalArgumentException("Invalid rule: rule or ruleId cannot be null");
        }
        
        ruleCache.put(rule.getRuleId(), rule);
        LOGGER.info("Rule registered: {}", rule.getRuleId());
    }
    
    @Override
    public void unregisterRule(String ruleId) {
        if (ruleCache.remove(ruleId) != null) {
            LOGGER.info("Rule unregistered: {}", ruleId);
        } else {
            LOGGER.warn("Attempted to unregister non-existent rule: {}", ruleId);
        }
    }
    
    @Override
    public void clearRuleCache() {
        ruleCache.clear();
        LOGGER.info("Rule cache cleared");
    }
    
    @Override
    public Map<String, Object> getPerformanceStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("totalExecutions", totalExecutions.get());
        stats.put("successfulExecutions", successfulExecutions.get());
        stats.put("failedExecutions", failedExecutions.get());
        stats.put("averageExecutionTime", totalExecutions.get() > 0 
                ? (double) executionTime.get() / totalExecutions.get() 
                : 0.0);
        stats.put("ruleCacheSize", ruleCache.size());
        return stats;
    }
    
    /**
     * 评估规则条件
     */
    private boolean evaluateCondition(String condition, Map<String, Object> context) {
        if (condition == null || condition.trim().isEmpty()) {
            // 如果条件为空，则默认为true
            return true;
        }
        
        try {
            return expressionEngine.evaluateBooleanExpression(condition, context);
        } catch (Exception e) {
            LOGGER.error("Failed to evaluate rule condition: {}", condition, e);
            throw new RuntimeException("Condition evaluation failed: " + condition, e);
        }
    }
    
    /**
     * 执行规则动作
     */
    private void executeActions(BusinessRule rule, Map<String, Object> context, RuleExecutionResult result) {
        if (rule.getActions() == null || rule.getActions().isEmpty()) {
            result.setMessage("No actions defined for rule");
            return;
        }
        
        StringBuilder actionResults = new StringBuilder();
        
        for (Map.Entry<String, String> action : rule.getActions().entrySet()) {
            String actionType = action.getKey();
            String actionExpression = action.getValue();
            
            try {
                Object actionResult = expressionEngine.eval(actionExpression, context);
                actionResults.append(actionType).append(": ").append(actionResult).append("; ");
                
                // 如果动作是设置上下文值，则更新上下文
                if (actionType.equals("setContext")) {
                    // 解析表达式，假设格式为 "key=value"
                    if (actionExpression.contains("=")) {
                        String[] parts = actionExpression.split("=", 2);
                        if (parts.length == 2) {
                            String key = parts[0].trim();
                            String value = parts[1].trim();
                            // 移除可能的引号
                            if (value.startsWith("\"") && value.endsWith("\"")) {
                                value = value.substring(1, value.length() - 1);
                            }
                            context.put(key, value);
                        }
                    }
                }
            } catch (Exception e) {
                String errorMsg = "Failed to execute action " + actionType + ": " + e.getMessage();
                actionResults.append(errorMsg).append("; ");
                LOGGER.error(errorMsg, e);
                // 根据规则配置决定是否继续执行其他动作
                if (rule.isFailFast()) {
                    result.setSuccess(false);
                    result.setErrorMessage(errorMsg);
                    return;
                }
            }
        }
        
        result.setMessage(actionResults.toString());
    }
    
    /**
     * 设置实例（仅用于测试）
     */
    protected static void setInstance(DefaultBusinessRuleEngine testInstance) {
        synchronized (DefaultBusinessRuleEngine.class) {
            instance = testInstance;
            initialized.set(true);
        }
    }
}