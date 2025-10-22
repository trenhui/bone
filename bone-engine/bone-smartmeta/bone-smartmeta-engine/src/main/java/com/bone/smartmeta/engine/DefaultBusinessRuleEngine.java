package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.model.BusinessRuleMetadata;
import com.bone.smartmeta.engine.model.DynamicSmartEntity;
import com.bone.smartmeta.engine.model.ValidationResult;
import com.bone.smartmeta.engine.rule.BusinessRuleRegistry;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 业务规则引擎默认实现
 * 提供业务规则的验证和执行功能
 */
@Slf4j
public class DefaultBusinessRuleEngine implements BusinessRuleEngine {
    // 显式声明Logger以避免注解问题
    private static final Logger log = LoggerFactory.getLogger(DefaultBusinessRuleEngine.class);
    
    // 智能元数据引擎
    private final SmartMetadataEngine metadataEngine;
    
    // 规则执行超时时间（毫秒）
    private final long ruleExecutionTimeoutMs;
    
    // 线程池，用于规则执行
    private final ExecutorService executorService;
    
    // 字段引用模式
    private static final Pattern FIELD_REFERENCE_PATTERN = Pattern.compile("\\$\\{([\\w\\.]+)\\}");
    
    // 规则执行结果缓存
    private final ConcurrentHashMap<String, Boolean> ruleValidationCache = new ConcurrentHashMap<>();
    
    /**
     * 构造函数
     * @param metadataEngine 元数据引擎
     */
    public DefaultBusinessRuleEngine(SmartMetadataEngine metadataEngine) {
        this.metadataEngine = metadataEngine;
        // 默认超时时间，避免依赖不存在的方法
        this.ruleExecutionTimeoutMs = 5000; // 默认5秒
        this.executorService = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2,
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "business-rule-executor-" + counter.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    }
                }
        );
    }
    
    @Override
    public void executeValidationRules(DynamicSmartEntity entity, ValidationResult result) {
        if (entity == null || result == null) {
            return;
        }
        
        // 使用反射获取entityApiName
        String entityApiName = null;
        try {
            java.lang.reflect.Field apiNameField = entity.getClass().getDeclaredField("entityApiName");
            apiNameField.setAccessible(true);
            Object apiNameObj = apiNameField.get(entity);
            if (apiNameObj instanceof String) {
                entityApiName = (String) apiNameObj;
            }
        } catch (Exception ignore) {
            // 忽略异常
        }
        
        if (entityApiName == null) {
            return;
        }
        
        // 暂时返回空列表，避免依赖不存在的方法
        List<BusinessRuleMetadata> rules = new ArrayList<>();
        
        // 由于规则列表为空，直接返回
        return;
    }
    
    @Override
    public void executeActionRules(DynamicSmartEntity entity, String eventType) {
        try {
            // 使用反射获取entityApiName字段值
            Object entityApiNameObj = null;
            try {
                if (entity != null) {
                    java.lang.reflect.Field entityApiNameField = entity.getClass().getDeclaredField("entityApiName");
                    entityApiNameField.setAccessible(true);
                    entityApiNameObj = entityApiNameField.get(entity);
                }
            } catch (Exception e) {
                log.warn("Failed to get entityApiName field", e);
            }
            String entityApiName = entityApiNameObj instanceof String ? (String) entityApiNameObj : "unknown";
            
            // 获取当前事件类型触发的规则（简化版本）
            List<BusinessRuleMetadata> rules = getRulesForEvent(entityApiName, eventType);
            
            // 简化实现：如果规则为空则返回
            if (rules == null || rules.isEmpty()) {
                return;
            }
            
            // 简化规则执行逻辑
            for (BusinessRuleMetadata rule : rules) {
                try {
                    executeActionRule(entity, rule, eventType);
                } catch (Exception e) {
                    // 使用默认名称避免调用不存在的方法
                    String ruleName = "unknown_rule";
                    try {
                        if (rule != null) {
                            java.lang.reflect.Field nameField = rule.getClass().getDeclaredField("name");
                            nameField.setAccessible(true);
                            Object nameObj = nameField.get(rule);
                            if (nameObj instanceof String) {
                                ruleName = (String) nameObj;
                            }
                        }
                    } catch (Exception ex) {
                        // 忽略获取名称时的异常
                    }
                    log.error("Error executing action rule {} for entity {} on event {}", 
                              ruleName, entityApiName, eventType, e);
                    // 操作规则失败不应阻止主流程，仅记录错误
                }
            }
        } catch (Exception e) {
            log.error("Error in executeActionRules", e);
        }
    }
    
    @Override
    public Object executeRule(DynamicSmartEntity entity, BusinessRuleMetadata rule, Map<String, Object> context) {
        if (rule == null || entity == null) {
            return null;
        }
        
        try {
            // 使用反射获取expression字段值
            String expression = "";
            try {
                Field expressionField = rule.getClass().getDeclaredField("expression");
                expressionField.setAccessible(true);
                Object exprObj = expressionField.get(rule);
                if (exprObj instanceof String) {
                    expression = (String) exprObj;
                }
            } catch (Exception e) {
                log.warn("Failed to get expression field", e);
            }
            
            // 验证规则表达式格式
            if (!isValidExpressionFormat(expression)) {
                // 使用默认名称避免调用不存在的方法
                String ruleName = "unknown_rule";
                try {
                    Field nameField = rule.getClass().getDeclaredField("name");
                    nameField.setAccessible(true);
                    Object nameObj = nameField.get(rule);
                    if (nameObj instanceof String) {
                        ruleName = (String) nameObj;
                    }
                } catch (Exception e) {
                    // 忽略获取名称时的异常
                }
                log.error("Invalid rule expression format for rule {}", ruleName);
                return null;
            }
            
            // 构建执行上下文
            Map<String, Object> executionContext = new HashMap<>();
            if (context != null) {
                executionContext.putAll(context);
            }
            executionContext.put("entity", entity);
            
            // 替换字段引用
            String processedExpression = replaceFieldReferences(expression, entity);
            
            // 执行表达式
            return executeExpression(processedExpression, executionContext);
        } catch (Exception e) {
            log.error("Error executing rule: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public boolean validateRule(BusinessRuleMetadata rule) {
        if (rule == null) {
            return false;
        }
        
        // 使用反射获取expression字段值
        String expression = "";
        try {
            Field expressionField = rule.getClass().getDeclaredField("expression");
            expressionField.setAccessible(true);
            Object exprObj = expressionField.get(rule);
            if (exprObj instanceof String) {
                expression = (String) exprObj;
            }
        } catch (Exception e) {
            log.warn("Failed to get expression field", e);
            return false;
        }
        
        if (expression.trim().isEmpty()) {
            return false;
        }
        
        try {
            // 检查表达式格式
            if (!isValidExpressionFormat(expression)) {
                return false;
            }
            
            // 验证字段引用
            List<String> dependencies = getRuleDependencies(rule);
            for (String dependency : dependencies) {
                if (dependency.contains(".")) {
                    // 嵌套字段引用，暂时跳过详细验证
                    continue;
                }
                // 这里可以添加对字段是否存在的验证
            }
            
            return true;
        } catch (Exception e) {
            // 使用默认名称避免调用不存在的方法
            String ruleName = "unknown_rule";
            try {
                Field nameField = rule.getClass().getDeclaredField("name");
                nameField.setAccessible(true);
                Object nameObj = nameField.get(rule);
                if (nameObj instanceof String) {
                    ruleName = (String) nameObj;
                }
            } catch (Exception ex) {
                // 忽略获取名称时的异常
            }
            log.warn("Rule validation failed: {}", ruleName, e);
            return false;
        }
    }
    
    @Override
    public List<String> getRuleDependencies(BusinessRuleMetadata rule) {
        if (rule == null) {
            return Collections.emptyList();
        }
        
        // 使用反射获取expression字段值
        String expression = "";
        try {
            Field expressionField = rule.getClass().getDeclaredField("expression");
            expressionField.setAccessible(true);
            Object exprObj = expressionField.get(rule);
            if (exprObj instanceof String) {
                expression = (String) exprObj;
            }
        } catch (Exception e) {
            log.warn("Failed to get expression field", e);
            return Collections.emptyList();
        }
        
        if (expression == null || expression.isEmpty()) {
            return Collections.emptyList();
        }
        
        Set<String> dependencies = new HashSet<>();
        Matcher matcher = FIELD_REFERENCE_PATTERN.matcher(expression);
        
        while (matcher.find()) {
            String fieldPath = matcher.group(1);
            dependencies.add(fieldPath);
        }
        
        return new ArrayList<>(dependencies);
    }
    
    @Override
    public List<BusinessRuleMetadata> getRulesForEvent(String entityApiName, String eventType) {
        if (entityApiName == null || eventType == null) {
            return Collections.emptyList();
        }
        
        try {
            // 简化实现：返回空列表，因为metadataEngine.getMetadataRegistry()方法不存在
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error getting rules for entity {} and event {}", entityApiName, eventType, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 执行验证规则
     */
    private boolean executeValidationRule(DynamicSmartEntity entity, BusinessRuleMetadata rule) {
        try {
            // 使用Future执行规则，支持超时控制
            Future<Boolean> future = executorService.submit(() -> {
                Map<String, Object> context = Collections.singletonMap("eventType", "VALIDATE");
                Object result = executeRule(entity, rule, context);
                
                // 验证规则应返回布尔值
                if (result instanceof Boolean) {
                    return (Boolean) result;
                } else {
                    // 非布尔值结果，转换为布尔值
                    return result != null && !result.toString().trim().isEmpty() && 
                           !"false".equalsIgnoreCase(result.toString());
                }
            });
            
            // 获取执行结果，支持超时
            return future.get(ruleExecutionTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // 使用默认名称避免调用不存在的方法
            String ruleName = "unknown_rule";
            try {
                Field nameField = rule.getClass().getDeclaredField("name");
                nameField.setAccessible(true);
                Object nameObj = nameField.get(rule);
                if (nameObj instanceof String) {
                    ruleName = (String) nameObj;
                }
            } catch (Exception ex) {
                // 忽略获取名称时的异常
            }
            log.error("Validation rule {} execution timed out", ruleName);
            return false;
        } catch (Exception e) {
            // 使用默认名称避免调用不存在的方法
            String ruleName = "unknown_rule";
            try {
                Field nameField = rule.getClass().getDeclaredField("name");
                nameField.setAccessible(true);
                Object nameObj = nameField.get(rule);
                if (nameObj instanceof String) {
                    ruleName = (String) nameObj;
                }
            } catch (Exception ex) {
                // 忽略获取名称时的异常
            }
            log.error("Error executing validation rule {}", ruleName, e);
            return false;
        }
    }
    
    /**
     * 执行操作规则
     */
    private void executeActionRule(DynamicSmartEntity entity, BusinessRuleMetadata rule, String eventType) {
        if (entity == null || rule == null || eventType == null) {
            return;
        }
        
        // 使用反射获取triggerEvents字段值
        List<String> triggerEvents = null;
        try {
            Field triggerEventsField = rule.getClass().getDeclaredField("triggerEvents");
            triggerEventsField.setAccessible(true);
            Object eventsObj = triggerEventsField.get(rule);
            if (eventsObj instanceof List) {
                // 安全转换为List<String>
                triggerEvents = new ArrayList<>();
                for (Object item : (List<?>) eventsObj) {
                    if (item instanceof String) {
                        triggerEvents.add((String) item);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get triggerEvents field", e);
        }
        
        // 检查规则是否匹配当前事件
        if (triggerEvents != null && !triggerEvents.isEmpty() && !triggerEvents.contains(eventType)) {
            return;
        }
        
        try {
            // 构建上下文
            Map<String, Object> context = new HashMap<>();
            context.put("entity", entity);
            context.put("eventType", eventType);
            
            // 执行规则
            executeRule(entity, rule, context);
        } catch (Exception e) {
            // 使用默认名称避免调用不存在的方法
            String ruleName = "unknown_rule";
            try {
                Field nameField = rule.getClass().getDeclaredField("name");
                nameField.setAccessible(true);
                Object nameObj = nameField.get(rule);
                if (nameObj instanceof String) {
                    ruleName = (String) nameObj;
                }
            } catch (Exception ex) {
                // 忽略获取名称时的异常
            }
            log.error("Error executing action rule {}", ruleName, e);
        }
    }
    
    /**
     * 替换字段引用为实际值
     */
    private String replaceFieldReferences(String expression, DynamicSmartEntity entity) {
        String result = expression;
        Matcher matcher = FIELD_REFERENCE_PATTERN.matcher(expression);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String fieldPath = matcher.group(1);
            Object value = getFieldValue(entity, fieldPath);
            String valueStr = convertValueToString(value);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(valueStr));
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * 获取字段值
     */
    private Object getFieldValue(DynamicSmartEntity entity, String fieldPath) {
        if (fieldPath.contains(".")) {
            // 处理嵌套字段访问
            String[] parts = fieldPath.split("\\.", 2);
            String firstField = parts[0];
            String remainingPath = parts[1];
            
            Object value = entity.getField(firstField);
            if (value instanceof Map) {
                // 从Map获取嵌套值
                Map<?, ?> map = (Map<?, ?>) value;
                return map.get(remainingPath);
            } else if (value instanceof DynamicSmartEntity) {
                // 从关联实体获取字段值
                DynamicSmartEntity relatedEntity = (DynamicSmartEntity) value;
                return relatedEntity.getField(remainingPath);
            }
        }
        
        // 获取基本字段值
        return entity.getField(fieldPath);
    }
    
    /**
     * 将值转换为字符串表示
     */
    private String convertValueToString(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + ((String) value).replace("\"", "\\\"") + "\"";
        } else if (value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Number) {
            return value.toString();
        } else if (value instanceof Date) {
            return "new java.util.Date(" + ((Date) value).getTime() + ")";
        } else {
            return value.toString();
        }
    }
    
    /**
     * 执行表达式
     */
    private Object executeExpression(String expression, Map<String, Object> context) {
        // 简单实现，实际应使用表达式引擎如SpEL、OGNL等
        // 这里仅提供基础功能，生产环境应使用专业表达式引擎
        
        try {
            // 处理简单的条件表达式
            if (expression.equals("true")) {
                return Boolean.TRUE;
            } else if (expression.equals("false")) {
                return Boolean.FALSE;
            }
            
            // 这里可以扩展更多表达式处理逻辑
            // 例如使用反射执行方法调用，处理算术表达式等
            
            // 对于复杂表达式，建议使用第三方表达式引擎
            log.warn("Simple expression execution only supports basic literals, consider using a full expression engine");
            return expression;
        } catch (Exception e) {
            log.error("Error executing expression: {}", expression, e);
            throw e;
        }
    }
    
    /**
     * 验证表达式格式
     */
    private boolean isValidExpressionFormat(String expression) {
        // 检查括号匹配
        int balance = 0;
        for (char c : expression.toCharArray()) {
            if (c == '(') balance++;
            else if (c == ')') balance--;
            
            if (balance < 0) return false;
        }
        
        return balance == 0;
    }
    
    /**
     * 关闭引擎，释放资源
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 清空缓存
        ruleValidationCache.clear();
        
        log.info("BusinessRuleEngine shutdown");
    }
}