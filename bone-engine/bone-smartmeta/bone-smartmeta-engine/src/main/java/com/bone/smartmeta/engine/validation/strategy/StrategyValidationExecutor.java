package com.bone.smartmeta.engine.validation.strategy;

import com.bone.smartmeta.engine.validation.ValidationResult;
import com.bone.smartmeta.engine.metadata.EntityMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 策略验证执行器
 * 协调执行所有注册的验证策略
 */
@Component
public class StrategyValidationExecutor {
    
    private static final Logger log = LoggerFactory.getLogger(StrategyValidationExecutor.class);
    
    // 默认验证策略执行顺序
    private static final List<String> DEFAULT_STRATEGY_ORDER = Arrays.asList(
            "requiredFieldValidation",    // 必填字段验证
            "fieldTypeValidation",        // 字段类型验证
            "fieldConstraintValidation",  // 字段约束验证
            "relationshipValidation",     // 关联字段验证
            "customRuleValidation"        // 自定义规则验证
    );
    
    private final ValidationStrategyFactory strategyFactory;
    private final Executor taskExecutor;
    
    @Autowired
    public StrategyValidationExecutor(ValidationStrategyFactory strategyFactory, Executor taskExecutor) {
        this.strategyFactory = strategyFactory;
        this.taskExecutor = taskExecutor;
    }
    
    /**
     * 执行所有验证策略
     * @param entityMetadata 实体元数据
     * @param entityData 实体数据
     * @param result 验证结果对象
     * @param failFast 是否快速失败
     */
    public void executeStrategies(EntityMetadata entityMetadata, Map<String, Object> entityData, 
                                 ValidationResult result, boolean failFast) {
        log.debug("开始执行验证策略，实体类型: {}, 快速失败模式: {}", 
                entityMetadata.getApiName(), failFast);
        
        // 按照预定义顺序执行策略
        for (String strategyName : DEFAULT_STRATEGY_ORDER) {
            ValidationStrategy strategy = strategyFactory.getStrategy(strategyName);
            if (strategy != null) {
                executeStrategy(strategy, entityMetadata, entityData, result);
                
                // 如果快速失败模式且已有错误，停止验证
                if (failFast && !result.isValid()) {
                    log.warn("快速失败模式：验证策略 {} 失败，提前返回", strategyName);
                    return;
                }
            } else {
                log.debug("跳过不存在的验证策略: {}", strategyName);
            }
        }
        
        // 执行其他未在默认顺序中的策略
        Map<String, ValidationStrategy> allStrategies = strategyFactory.getAllStrategies();
        for (Map.Entry<String, ValidationStrategy> entry : allStrategies.entrySet()) {
            String strategyName = entry.getKey();
            if (!DEFAULT_STRATEGY_ORDER.contains(strategyName)) {
                ValidationStrategy strategy = entry.getValue();
                executeStrategy(strategy, entityMetadata, entityData, result);
                
                // 如果快速失败模式且已有错误，停止验证
                if (failFast && !result.isValid()) {
                    log.warn("快速失败模式：验证策略 {} 失败，提前返回", strategyName);
                    return;
                }
            }
        }
        
        log.debug("所有验证策略执行完成，实体类型: {}, 是否有效: {}, 错误数量: {}", 
                entityMetadata.getApiName(), result.isValid(), result.getErrors().size());
    }
    
    /**
     * 执行单个验证策略
     */
    private void executeStrategy(ValidationStrategy strategy, EntityMetadata entityMetadata, 
                                Map<String, Object> entityData, ValidationResult result) {
        try {
            log.debug("执行验证策略: {}", strategy.getName());
            strategy.validate(entityMetadata, entityData, result);
        } catch (Exception e) {
            log.error("执行验证策略 {} 时发生异常", strategy.getName(), e);
            // 添加异常信息到验证结果，但不中断其他策略的执行
            ValidationResult.ValidationWarning validationWarning = 
                ValidationResult.ValidationWarning.builder()
                    .fieldPath("general")
                    .message("验证策略 " + strategy.getName() + " 执行异常: " + e.getMessage())
                    .build();
            result.addWarning(validationWarning);
        }
    }
    
    /**
     * 异步执行验证策略
     * @param entityMetadata 实体元数据
     * @param entityData 实体数据
     * @return 包含验证结果的CompletableFuture
     */
    public CompletableFuture<ValidationResult> executeStrategiesAsync(EntityMetadata entityMetadata, 
                                                                    Map<String, Object> entityData, 
                                                                    boolean failFast) {
        return CompletableFuture.supplyAsync(() -> {
            ValidationResult result = ValidationResult.builder().build();
            executeStrategies(entityMetadata, entityData, result, failFast);
            return result;
        }, taskExecutor);
    }
    
    /**
     * 批量异步验证
     * @param entityMetadata 实体元数据
     * @param entityDataList 实体数据列表
     * @return 验证结果列表的CompletableFuture
     */
    public CompletableFuture<List<ValidationResult>> executeBatchStrategiesAsync(EntityMetadata entityMetadata, 
                                                                                List<Map<String, Object>> entityDataList, 
                                                                                boolean failFast) {
        log.debug("开始批量异步验证，实体类型: {}, 记录数量: {}", 
                entityMetadata.getApiName(), entityDataList.size());
        
        List<CompletableFuture<ValidationResult>> futures = entityDataList.stream()
                .map(data -> executeStrategiesAsync(entityMetadata, data, failFast))
                .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }
    
    /**
     * 自定义验证策略执行顺序
     * @param entityMetadata 实体元数据
     * @param entityData 实体数据
     * @param result 验证结果对象
     * @param strategyNames 自定义的策略执行顺序
     */
    public void executeStrategiesInOrder(EntityMetadata entityMetadata, Map<String, Object> entityData, 
                                        ValidationResult result, List<String> strategyNames) {
        for (String strategyName : strategyNames) {
            ValidationStrategy strategy = strategyFactory.getStrategy(strategyName);
            if (strategy != null) {
                executeStrategy(strategy, entityMetadata, entityData, result);
            } else {
                log.debug("跳过不存在的验证策略: {}", strategyName);
            }
        }
    }
}
