package com.bone.smartmeta.engine.validation.strategy;

import com.bone.smartmeta.engine.RuleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * 验证策略工厂
 * 创建和管理各种验证策略实例
 */
@Component
public class ValidationStrategyFactory {
    
    private static final Logger log = LoggerFactory.getLogger(ValidationStrategyFactory.class);
    private final Map<String, ValidationStrategy> strategies = new HashMap<>();
    
    @Autowired(required = false)
    private RuleEngine ruleEngine;
    
    /**
     * 初始化策略工厂
     * 注册所有可用的验证策略
     */
    @Autowired(required = false)
    public void init(@Lazy RelationshipValidationStrategy.EntityMetadataProvider metadataProvider) {
        // 注册内置策略
        registerDefaultStrategies(metadataProvider);
        
        // 通过SPI机制发现并注册其他策略
        discoverStrategies();
        
        log.info("验证策略工厂初始化完成，已注册策略数量: {}", strategies.size());
    }
    
    /**
     * 注册默认验证策略
     */
    private void registerDefaultStrategies(RelationshipValidationStrategy.EntityMetadataProvider metadataProvider) {
        // 注册必填字段验证策略
        registerStrategy(new RequiredFieldValidationStrategy());
        
        // 注册字段类型验证策略
        registerStrategy(new FieldTypeValidationStrategy());
        
        // 注册字段约束验证策略
        registerStrategy(new FieldConstraintValidationStrategy());
        
        // 注册自定义规则验证策略
        registerStrategy(new CustomRuleValidationStrategy(ruleEngine));
        
        // 注册关联字段验证策略
        if (metadataProvider != null) {
            registerStrategy(new RelationshipValidationStrategy(metadataProvider));
        }
    }
    
    /**
     * 通过SPI机制发现验证策略
     */
    private void discoverStrategies() {
        try {
            ServiceLoader<ValidationStrategy> serviceLoader = ServiceLoader.load(ValidationStrategy.class);
            for (ValidationStrategy strategy : serviceLoader) {
                registerStrategy(strategy);
                log.info("通过SPI发现并注册验证策略: {}", strategy.getName());
            }
        } catch (Exception e) {
            log.warn("SPI机制发现验证策略失败: {}", e.getMessage());
        }
    }
    
    /**
     * 注册验证策略
     * @param strategy 验证策略实例
     */
    public void registerStrategy(ValidationStrategy strategy) {
        if (strategy != null) {
            String strategyName = strategy.getName();
            strategies.put(strategyName, strategy);
            log.debug("注册验证策略: {}", strategyName);
        }
    }
    
    /**
     * 获取验证策略
     * @param strategyName 策略名称
     * @return 验证策略实例，不存在则返回null
     */
    public ValidationStrategy getStrategy(String strategyName) {
        return strategies.get(strategyName);
    }
    
    /**
     * 获取所有验证策略
     * @return 所有已注册的验证策略
     */
    public Map<String, ValidationStrategy> getAllStrategies() {
        return new HashMap<>(strategies);
    }
    
    /**
     * 检查策略是否存在
     * @param strategyName 策略名称
     * @return 是否存在
     */
    public boolean hasStrategy(String strategyName) {
        return strategies.containsKey(strategyName);
    }
}
