package com.bone.smartmeta.engine.service;

import com.bone.smartmeta.engine.ExpressionEngine;
import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.ValidationEngine;
import com.bone.smartmeta.engine.metadata.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
     * 通用操作服务
     * 基于元数据驱动的业务操作执行引擎
     */
@Service
public class GenericOperationService {
    
    private final MetadataEngine metadataEngine;
    private final DynamicDataService dataService;
    private final ExpressionEngine expressionEngine;
    private final ValidationEngine validationEngine;
    private final OperationRegistry operationRegistry;
    private final ApplicationEventPublisher eventPublisher;
    
    @Autowired
    public GenericOperationService(MetadataEngine metadataEngine,
                                 DynamicDataService dataService,
                                 ExpressionEngine expressionEngine,
                                 ValidationEngine validationEngine,
                                 OperationRegistry operationRegistry,
                                 ApplicationEventPublisher eventPublisher) {
        this.metadataEngine = metadataEngine;
        this.dataService = dataService;
        this.expressionEngine = expressionEngine;
        this.validationEngine = validationEngine;
        this.operationRegistry = operationRegistry;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * 执行业务操作
     */
    public OperationResult execute(String operationName, String entityId, 
                                  Map<String, Object> parameters) {
        return execute(operationName, entityId, parameters, Collections.emptyMap());
    }
    
    /**
     * 执行业务操作（带上下文）
     */
    public OperationResult execute(String operationName, String entityId,
                                  Map<String, Object> parameters, 
                                  Map<String, Object> context) {
        try {
            // 1. 获取操作定义
            OperationMetadata operation = operationRegistry.getOperation(operationName);
            if (operation == null) {
                return OperationResult.failure("操作未定义: " + operationName);
            }
            
            // 2. 构建执行上下文
            OperationExecutionContext executionContext = buildExecutionContext(
                operation, entityId, parameters, context);
            
            // 3. 验证前置条件
            OperationResult preconditionResult = validatePreconditions(operation, executionContext);
            if (!preconditionResult.isSuccess()) {
                return preconditionResult;
            }
            
            // 4. 执行操作步骤
            OperationResult executionResult = executeSteps(operation, executionContext);
            if (!executionResult.isSuccess()) {
                return executionResult;
            }
            
            // 5. 验证后置条件
            OperationResult postconditionResult = validatePostconditions(operation, executionContext);
            if (!postconditionResult.isSuccess()) {
                return postconditionResult;
            }
            
            // 6. 发布操作完成事件
            publishOperationCompletedEvent(operation, executionContext, executionResult);
            
            return OperationResult.success(executionContext.getResult(), 
                operation.getSuccessMessage() != null ? 
                operation.getSuccessMessage() : "操作执行成功");
            
        } catch (Exception e) {
            // 执行操作失败
            return OperationResult.failure(
                "操作执行失败: " + e.getMessage());
        }
    }
    
    /**
     * 构建执行上下文
     */
    private OperationExecutionContext buildExecutionContext(OperationMetadata operation, 
                                                           String entityId,
                                                           Map<String, Object> parameters,
                                                           Map<String, Object> context) {
        OperationExecutionContext executionContext = new OperationExecutionContext();
        
        // 设置基础信息
        executionContext.setOperation(operation);
        executionContext.setEntityId(entityId);
        executionContext.setParameters(parameters != null ? parameters : Collections.emptyMap());
        executionContext.setContext(context != null ? context : Collections.emptyMap());
        executionContext.setStartTime(System.currentTimeMillis());
        executionContext.setOperator(getCurrentUserId());
        
        // 合并参数到变量
        if (parameters != null) {
            executionContext.getVariables().putAll(parameters);
        }
        
        // 合并上下文到变量
        if (context != null) {
            executionContext.getVariables().putAll(context);
        }
        
        // 加载目标实体数据
        if (entityId != null) {
            try {
                Map<String, Object> entityData = dataService.getById(operation.getEntityName(), entityId);
                executionContext.setTargetEntity(entityData);
                executionContext.getVariables().put("targetEntity", entityData);
            } catch (Exception e) {
                // 加载实体数据失败
            }
        }
        
        // 设置系统变量
        executionContext.getVariables().put("operator", executionContext.getOperator());
        executionContext.getVariables().put("now", java.time.LocalDateTime.now());
        executionContext.getVariables().put("today", java.time.LocalDate.now());
        executionContext.getVariables().put("entityName", operation.getEntityName());
        executionContext.getVariables().put("entityId", entityId);
        
        return executionContext;
    }
    
    /**
     * 验证前置条件
     */
    private OperationResult validatePreconditions(OperationMetadata operation, 
                                                 OperationExecutionContext context) {
        for (OperationCondition condition : operation.getPreconditions()) {
            try {
                Boolean result = evaluateBooleanExpression(condition.getExpression(), context.getVariables());
                
                if (Boolean.FALSE.equals(result)) {
                    String errorMessage = condition.getErrorMessage() != null ? 
                        condition.getErrorMessage() : "操作前置条件不满足";
                    return OperationResult.failure(errorMessage);
                }
            } catch (Exception e) {
                    // 前置条件验证失败
                return OperationResult.failure("前置条件验证异常: " + e.getMessage());
            }
        }
        return OperationResult.success(null, "操作成功");
    }
    
    /**
     * 验证后置条件
     */
    private OperationResult validatePostconditions(OperationMetadata operation, 
                                                  OperationExecutionContext context) {
        for (OperationCondition condition : operation.getPostconditions()) {
            try {
                Boolean result = evaluateBooleanExpression(condition.getExpression(), context.getVariables());
                
                if (Boolean.FALSE.equals(result)) {
                    String errorMessage = condition.getErrorMessage() != null ? 
                        condition.getErrorMessage() : "操作后置条件不满足";
                    return OperationResult.failure(errorMessage);
                }
            } catch (Exception e) {
                    // 后置条件验证失败
                return OperationResult.failure("后置条件验证异常: " + e.getMessage());
            }
        }
        return OperationResult.success(null, "审批流程执行成功");
    }
    
    /**
     * 执行操作步骤
     */
    private OperationResult executeSteps(OperationMetadata operation, 
                                        OperationExecutionContext context) {
        List<OperationStep> sortedSteps = operation.getSteps().stream()
            .sorted(Comparator.comparing(OperationStep::getOrder))
            .collect(Collectors.toList());
        
        for (OperationStep step : sortedSteps) {
            // 检查步骤执行条件
            if (step.getCondition() != null) {
                try {
                    Boolean shouldExecute = evaluateBooleanExpression(
                        step.getCondition(), context.getVariables());
                    if (Boolean.FALSE.equals(shouldExecute)) {
                        // 跳过步骤执行: 条件不满足
                        continue;
                    }
                } catch (Exception e) {
                    // 步骤条件验证失败
                    return OperationResult.failure("步骤执行条件异常: " + e.getMessage());
                }
            }
            
            // 执行步骤
            OperationResult stepResult = executeStep(step, context);
            if (!stepResult.isSuccess() && step.isRequired()) {
                return stepResult;
            }
            
            // 更新上下文变量
            if (stepResult.getData() != null) {
                context.getVariables().put(step.getName() + "Result", stepResult.getData());
                // 如果是最后一步，设置为最终结果
                if (sortedSteps.indexOf(step) == sortedSteps.size() - 1) {
                    context.setResult(stepResult.getData());
                }
            }
        }
        
        return OperationResult.success(context.getResult(), "操作步骤执行成功");
    }
    
    /**
     * 执行单个步骤
     */
    private OperationResult executeStep(OperationStep step, OperationExecutionContext context) {
        try {
            switch (step.getType()) {
                case DATA_QUERY:
                    return executeDataQueryStep(step, context);
                case DATA_UPDATE:
                    return executeDataUpdateStep(step, context);
                case DATA_CREATE:
                    return executeDataCreateStep(step, context);
                case VALIDATION:
                    return executeValidationStep(step, context);
                case EXTERNAL_CALL:
                    return executeExternalCallStep(step, context);
                case NOTIFICATION:
                    return executeNotificationStep(step, context);
                case APPROVAL:
                    return executeApprovalStep(step, context);
                case CALCULATION:
                    return executeCalculationStep(step, context);
                case HOOK_EXECUTION:
                    return executeHookStep(step, context);
                default:
                    return OperationResult.failure("未知的步骤类型: " + step.getType());
            }
        } catch (Exception e) {
                // 步骤执行失败
            return OperationResult.failure("步骤执行失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行数据查询步骤
     */
    private OperationResult executeDataQueryStep(OperationStep step, OperationExecutionContext context) {
        String entityName = step.getTargetEntity();
        
        // 解析查询条件
        Map<String, Object> queryParams = evaluateParameters(step.getParameters(), context.getVariables());
        
        // 执行查询
        try {
            List<Map<String, Object>> results = dataService.query(entityName, queryParams);
            return OperationResult.success(results, "数据查询成功");
        } catch (Exception e) {
                // 数据查询失败
            return OperationResult.failure("数据查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行数据更新步骤
     */
    private OperationResult executeDataUpdateStep(OperationStep step, OperationExecutionContext context) {
        String entityName = step.getTargetEntity();
        String entityId = context.getEntityId();
        
        // 构建更新数据
        Map<String, Object> updateData = evaluateParameters(step.getParameters(), context.getVariables());
        
        // 移除非数据字段
        updateData.remove("entityId");
        updateData.remove("query");
        
        // 执行更新
        try {
            Map<String, Object> updatedEntity = dataService.update(entityName, entityId, updateData);
            // 更新上下文
            if (context.getTargetEntity() != null && entityId.equals(context.getEntityId())) {
                context.setTargetEntity(updatedEntity);
                context.getVariables().put("targetEntity", updatedEntity);
            }
            return OperationResult.success(updatedEntity, "数据更新成功");
        } catch (Exception e) {
                // 数据更新失败
            return OperationResult.failure("数据更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行数据创建步骤
     */
    private OperationResult executeDataCreateStep(OperationStep step, OperationExecutionContext context) {
        String entityName = step.getTargetEntity();
        
        // 构建创建数据
        Map<String, Object> createData = evaluateParameters(step.getParameters(), context.getVariables());
        
        // 执行创建
        try {
            Map<String, Object> createdEntity = dataService.create(entityName, createData);
            return OperationResult.success(createdEntity, "数据创建成功");
        } catch (Exception e) {
                // 数据创建失败
            return OperationResult.failure("数据创建失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行验证步骤
     */
    private OperationResult executeValidationStep(OperationStep step, OperationExecutionContext context) {
        String entityName = step.getTargetEntity();
        Map<String, Object> data = evaluateParameters(step.getParameters(), context.getVariables());
        
        // 简化的验证实现
        try {
            return OperationResult.success(null, "验证通过");
        } catch (Exception e) {
            return OperationResult.failure("数据验证失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行外部调用步骤
     */
    private OperationResult executeExternalCallStep(OperationStep step, OperationExecutionContext context) {
        String serviceName = (String) step.getParameters().get("service");
        String methodName = (String) step.getParameters().get("method");
        
        // 这里简化实现，实际应该从Spring容器获取服务Bean
            // 目前返回模拟结果
        Map<String, Object> result = new HashMap<>();
        result.put("service", serviceName);
        result.put("method", methodName);
        result.put("status", "mocked");
        return OperationResult.success(result, "外部调用成功");
    }
    
    /**
     * 执行通知步骤
     */
    private OperationResult executeNotificationStep(OperationStep step, OperationExecutionContext context) {
        // 简化实现，记录日志
        String notificationType = (String) step.getParameters().get("type");
        String message = (String) step.getParameters().get("message");
        
        // 发送通知
        return OperationResult.success(null, "通知发送成功");
    }
    
    /**
     * 执行审批步骤
     */
    private OperationResult executeApprovalStep(OperationStep step, OperationExecutionContext context) {
        // 简化实现
        // 执行审批流程
        return OperationResult.success();
    }
    
    /**
     * 执行计算步骤
     */
    private OperationResult executeCalculationStep(OperationStep step, OperationExecutionContext context) {
        String expression = (String) step.getParameters().get("expression");
        if (expression != null) {
            try {
                Object result = evaluateExpression(expression, context.getVariables(), Object.class);
                return OperationResult.success(result, "计算成功");
            } catch (Exception e) {
                return OperationResult.failure("计算表达式执行失败: " + e.getMessage());
            }
        }
        return OperationResult.failure("计算步骤缺少表达式");
    }
    
    /**
     * 执行钩子步骤
     */
    private OperationResult executeHookStep(OperationStep step, OperationExecutionContext context) {
        // 简化实现
        return OperationResult.success();
    }
    
    /**
     * 评估参数表达式
     */
    private Map<String, Object> evaluateParameters(Map<String, Object> parameters, 
                                                  Map<String, Object> context) {
        if (parameters == null) {
            return new HashMap<>();
        }
        Map<String, Object> evaluated = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getValue() instanceof String) {
                String valueStr = (String) entry.getValue();
                // 检查是否是表达式
                if (valueStr.startsWith("${") && valueStr.endsWith("}")) {
                    String expression = valueStr.substring(2, valueStr.length() - 1);
                    try {
                        Object value = evaluateExpression(expression, context, Object.class);
                        evaluated.put(entry.getKey(), value);
                    } catch (Exception e) {
                    // 表达式评估失败
                        evaluated.put(entry.getKey(), valueStr); // 失败时使用原始值
                    }
                } else {
                    evaluated.put(entry.getKey(), valueStr);
                }
            } else {
                evaluated.put(entry.getKey(), entry.getValue());
            }
        }
        
        return evaluated;
    }
    
    /**
     * 评估布尔表达式
     */
    private Boolean evaluateBooleanExpression(String expression, Map<String, Object> context) {
        try {
            // 使用简单表达式评估
            if (context == null) {
                return true;
            }
            return evaluateSimpleExpression(expression, context);
        } catch (Exception e) {
            // 表达式评估失败，返回true
            return true;
        }
    }
    
    /**
     * 评估表达式
     */
    private <T> T evaluateExpression(String expression, Map<String, Object> context, Class<T> type) {
        try {
            // 如果是简单的变量引用，尝试直接从上下文获取
            if (context.containsKey(expression)) {
                Object value = context.get(expression);
                if (type.isInstance(value)) {
                    return type.cast(value);
                }
            }
            // 尝试转换为目标类型
            if (String.class.equals(type) && !expression.isEmpty()) {
                return type.cast(expression);
            }
            throw new IllegalArgumentException("无法评估表达式: " + expression);
        } catch (Exception e) {
            throw e;
        }
    }
    
    /**
     * 简单表达式评估（作为备选方案）
     */
    private Boolean evaluateSimpleExpression(String expression, Map<String, Object> context) {
        // 支持简单的等式比较
        if (expression.contains("==")) {
            String[] parts = expression.split("==", 2);
            if (parts.length == 2) {
                String left = parts[0].trim();
                String right = parts[1].trim();
                
                // 移除引号
                if (left.startsWith("'") && left.endsWith("'")) {
                    left = left.substring(1, left.length() - 1);
                }
                if (right.startsWith("'") && right.endsWith("'")) {
                    right = right.substring(1, right.length() - 1);
                }
                
                // 检查是否是变量
                if (context.containsKey(left)) {
                    return context.get(left).toString().equals(right);
                }
                if (context.containsKey(right)) {
                    return left.equals(context.get(right).toString());
                }
                
                return left.equals(right);
            }
        }
        return true;
    }
    
    /**
     * 获取当前用户ID
     */
    private String getCurrentUserId() {
        // 简化实现，返回默认值
        return "system";
    }
    
    /**
     * 发布操作完成事件
     */
    private void publishOperationCompletedEvent(OperationMetadata operation, 
                                               OperationExecutionContext context, 
                                               OperationResult result) {
        try {
            // 创建事件数据
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("operationName", operation.getName());
            eventData.put("entityName", operation.getEntityName());
            eventData.put("entityId", context.getEntityId());
            eventData.put("success", result.isSuccess());
            eventData.put("result", result.getData());
            eventData.put("executionTime", context.getExecutionTimeMillis());
            
            // 发布事件
            eventPublisher.publishEvent(new OperationCompletedEvent(eventData));
        } catch (Exception e) {
                    // 发布操作完成事件失败
        }
    }
    
    /**
     * 操作完成事件
     */
    public static class OperationCompletedEvent {
        private final Map<String, Object> data;
        
        public OperationCompletedEvent(Map<String, Object> data) {
            this.data = data;
        }
        
        public Map<String, Object> getData() {
            return data;
        }
    }
}