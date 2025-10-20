package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.model.BusinessRuleMetadata;
import com.bone.smartmeta.engine.model.DynamicSmartEntity;
import com.bone.smartmeta.engine.model.ValidationResult;
import com.bone.smartmeta.engine.rule.BusinessRuleRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 业务规则引擎默认实现
 * 提供业务规则的验证和执行功能
 */
@Slf4j
public class DefaultBusinessRuleEngine implements BusinessRuleEngine {
    
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
        this.ruleExecutionTimeoutMs = metadataEngine.getConfiguration().getRuleExecutionTimeoutMs();
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
        if (entity == null || entity.getEntityApiName() == null) {
            return;
        }
        
        // 获取实体的所有业务规则
        List<BusinessRuleMetadata> rules = metadataEngine.getMetadataRegistry()
                .getBusinessRuleMetadata(entity.getEntityApiName());
        
        if (rules.isEmpty()) {
            return;
        }
        
        // 过滤出验证类型的规则
        List<BusinessRuleMetadata> validationRules = rules.stream()
                .filter(rule -> rule.getRuleType() == BusinessRuleMetadata.RuleType.VALIDATION)
                .sorted(Comparator.comparingInt(BusinessRuleMetadata::getPriority).reversed())
                .collect(Collectors.toList());
        
        // 执行验证规则
        for (BusinessRuleMetadata rule : validationRules) {
            try {
                boolean isValid = executeValidationRule(entity, rule);
                if (!isValid) {
                    result.addError(rule.getErrorMessage() != null ? rule.getErrorMessage() : 
                                   "Business rule violated: " + rule.getName());
                }
            } catch (Exception e) {
                log.error("Error executing validation rule {} for entity {}", 
                          rule.getName(), entity.getEntityApiName(), e);
                // 默认规则验证失败，确保数据安全
                result.addError("Error evaluating business rule: " + rule.getName());
            }
        }
    }
    
    @Override
    public void executeActionRules(DynamicSmartEntity entity, String eventType) {
        if (entity == null || entity.getEntityApiName() == null || eventType == null) {
            return;
        }
        
        // 获取当前事件类型触发的规则
        List<BusinessRuleMetadata> rules = getRulesForEvent(entity.getEntityApiName(), eventType);
        
        if (rules.isEmpty()) {
            return;
        }
        
        // 过滤出操作类型的规则
        List<BusinessRuleMetadata> actionRules = rules.stream()
                .filter(rule -> rule.getRuleType() == BusinessRuleMetadata.RuleType.ACTION)
                .sorted(Comparator.comparingInt(BusinessRuleMetadata::getPriority).reversed())
                .collect(Collectors.toList());
        
        // 执行操作规则
        for (BusinessRuleMetadata rule : actionRules) {
            try {
                executeActionRule(entity, rule, eventType);
            } catch (Exception e) {
                log.error("Error executing action rule {} for entity {} on event {}", 
                          rule.getName(), entity.getEntityApiName(), eventType, e);
                // 操作规则失败不应阻止主流程，仅记录错误
            }
        }
    }
    
    @Override
    public Object executeRule(DynamicSmartEntity entity, BusinessRuleMetadata rule, Map<String, Object> context) {
        if (rule == null || rule.getExpression() == null || rule.getExpression().trim().isEmpty()) {
            throw new IllegalArgumentException("Rule expression cannot be empty");
        }
        
        try {
            // 创建上下文
            Map<String, Object> executionContext = new HashMap<>();
            
            // 添加实体字段值到上下文
            Map<String, Object> allFields = entity.getAllFields();
            executionContext.putAll(allFields);
            
            // 添加实体引用
            executionContext.put("entity", entity);
            executionContext.put("metadataEngine", metadataEngine);
            
            // 添加自定义上下文
            if (context != null) {
                executionContext.putAll(context);
            }
            
            // 替换字段引用
            String processedExpression = replaceFieldReferences(rule.getExpression(), entity);
            
            // 执行规则（这里使用简单实现，实际应使用表达式引擎）
            return executeExpression(processedExpression, executionContext);
        } catch (Exception e) {
            log.error("Error executing rule {}", rule.getName(), e);
            throw new RuntimeException("Failed to execute rule: " + rule.getName(), e);
        }
    }
    
    @Override
    public boolean validateRule(BusinessRuleMetadata rule) {
        if (rule == null || rule.getExpression() == null || rule.getExpression().trim().isEmpty()) {
            return false;
        }
        
        try {
            // 检查表达式格式
            if (!isValidExpressionFormat(rule.getExpression())) {
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
            log.warn("Rule validation failed: {}", rule.getName(), e);
            return false;
        }
    }
    
    @Override
    public List<String> getRuleDependencies(BusinessRuleMetadata rule) {
        if (rule == null || rule.getExpression() == null) {
            return Collections.emptyList();
        }
        
        Set<String> dependencies = new HashSet<>();
        Matcher matcher = FIELD_REFERENCE_PATTERN.matcher(rule.getExpression());
        
        while (matcher.find()) {
            String fieldPath = matcher.group(1);
            dependencies.add(fieldPath);
        }
        
        return new ArrayList<>(dependencies);
    }
    
    @Override
    public List<BusinessRuleMetadata> getRulesForEvent(String entityApiName, String eventType) {
        List<BusinessRuleMetadata> allRules = metadataEngine.getMetadataRegistry()
                .getBusinessRuleMetadata(entityApiName);
        
        if (allRules.isEmpty()) {
            return Collections.emptyList();
        }
        
        return allRules.stream()
                .filter(rule -> rule.getTriggerEvents() != null && 
                               rule.getTriggerEvents().contains(eventType))
                .collect(Collectors.toList());
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
            log.error("Validation rule {} execution timed out", rule.getName());
            return false;
        } catch (Exception e) {
            log.error("Error executing validation rule {}", rule.getName(), e);
            return false;
        }
    }
    
    /**
     * 执行操作规则
     */
    private void executeActionRule(DynamicSmartEntity entity, BusinessRuleMetadata rule, String eventType) {
        try {
            Map<String, Object> context = new HashMap<>();
            context.put("eventType", eventType);
            context.put("rule", rule);
            
            executeRule(entity, rule, context);
        } catch (Exception e) {
            log.error("Error executing action rule {}", rule.getName(), e);
            throw e;
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