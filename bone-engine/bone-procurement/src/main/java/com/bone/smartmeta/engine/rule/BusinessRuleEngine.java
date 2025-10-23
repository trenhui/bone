package com.bone.smartmeta.engine.rule;

import com.bone.smartmeta.engine.model.ValidationResult;
import java.util.Map;

/**
 * 业务规则引擎接口
 * 定义业务规则引擎的核心功能，支持不同类型规则的执行和评估
 */
public interface BusinessRuleEngine {

    /**
     * 评估高价值订单规则
     * @param orderData 订单数据
     * @return 规则执行结果
     * @throws IllegalArgumentException 当参数验证失败时抛出
     */
    ValidationResult evaluateHighValueOrderRule(Map<String, Object> orderData);

    /**
     * 评估订单优先级规则
     * @param orderData 订单数据
     * @return 规则执行结果
     * @throws IllegalArgumentException 当参数验证失败时抛出
     */
    ValidationResult evaluateOrderPriorityRule(Map<String, Object> orderData);

    /**
     * 执行指定类型的业务规则
     * @param entity 实体对象
     * @param ruleType 规则类型
     * @return 规则执行结果
     * @throws IllegalArgumentException 当参数验证失败时抛出
     */
    ValidationResult executeRules(Object entity, String ruleType);
}