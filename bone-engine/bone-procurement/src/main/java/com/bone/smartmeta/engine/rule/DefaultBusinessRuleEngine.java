package com.bone.smartmeta.engine.rule;

import com.bone.smartmeta.engine.constant.ErrorCodeConstants;
import com.bone.smartmeta.engine.constant.RuleTypeConstants;
import com.bone.smartmeta.engine.model.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * 默认采购业务规则引擎实现
 * 基于公共的ValidationResult类实现规则评估功能，包括订单金额验证、优先级确定等
 */
@Component
public class DefaultBusinessRuleEngine implements BusinessRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultBusinessRuleEngine.class);
    
    // 常量定义
    private static final double HIGH_VALUE_THRESHOLD = 100000.0;

    /**
     * 评估高价值订单规则
     * @param orderData 订单数据
     * @return 规则执行结果
     * @throws IllegalArgumentException 当参数验证失败时抛出
     */
    @Override
    public ValidationResult evaluateHighValueOrderRule(Map<String, Object> orderData) {
        log.debug("开始评估高价值订单规则");
        
        ValidationResult result = ValidationResult.builder()
                .isValid(true)
                .build();
        
        try {
            // 参数验证
            if (orderData == null) {
                String errorMsg = "订单数据为空";
                log.warn(errorMsg);
                result.addGlobalError(errorMsg);
                return result;
            }
            
            // 检查订单金额
            Object amountObj = orderData.get("amount");
            if (amountObj instanceof Number) {
                double amount = ((Number) amountObj).doubleValue();
                log.debug("评估订单金额: {}", amount);
                
                if (amount > HIGH_VALUE_THRESHOLD) {
                    // 高价值订单需要特殊审批流程
                    String errorCode = ErrorCodeConstants.HIGH_VALUE_ORDER_REQUIRES_APPROVAL;
                    String errorMsg = "高价值订单需要特殊审批";
                    log.info("订单金额 {} 超过高价值阈值 {}，需要特殊审批", amount, HIGH_VALUE_THRESHOLD);
                    result.addGlobalError(errorMsg, errorCode);
                }
            } else {
                log.warn("订单金额不是有效数字类型: {}", amountObj);
            }
        } catch (IllegalArgumentException e) {
            log.error("评估高价值订单规则参数验证失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            String errorCode = ErrorCodeConstants.RULE_EVALUATION_FAILED;
            String errorMsg = "规则评估失败: " + e.getMessage();
            log.error("评估高价值订单规则异常", e);
            result.addGlobalError(errorMsg, errorCode);
        }
        
        log.debug("高价值订单规则评估完成，结果: {}", result.isValid() ? "有效" : "无效");
        return result;
    }

    /**
     * 评估订单优先级规则
     * @param orderData 订单数据
     * @return 规则执行结果
     * @throws IllegalArgumentException 当参数验证失败时抛出
     */
    @Override
    public ValidationResult evaluateOrderPriorityRule(Map<String, Object> orderData) {
        log.debug("开始评估订单优先级规则");
        
        ValidationResult result = ValidationResult.builder()
                .isValid(true)
                .build();
        
        try {
            // 参数验证
            if (orderData == null) {
                String errorMsg = "订单数据为空";
                log.warn(errorMsg);
                result.addGlobalError(errorMsg);
                return result;
            }
            
            // 检查紧急程度
            Object urgentObj = orderData.get("urgent");
            log.debug("检查订单紧急程度: {}", urgentObj);
            
            if (urgentObj instanceof Boolean && (Boolean) urgentObj) {
                // 紧急订单设置高优先级
                log.info("发现紧急订单，设置高优先级");
                orderData.put("priority", "HIGH");
            }
        } catch (IllegalArgumentException e) {
            log.error("评估订单优先级规则参数验证失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            String errorCode = ErrorCodeConstants.RULE_EVALUATION_FAILED;
            String errorMsg = "规则评估失败: " + e.getMessage();
            log.error("评估订单优先级规则异常", e);
            result.addGlobalError(errorMsg, errorCode);
        }
        
        log.debug("订单优先级规则评估完成");
        return result;
    }

    /**
     * 执行指定类型的业务规则
     * @param entity 实体对象
     * @param ruleType 规则类型
     * @return 规则执行结果
     * @throws IllegalArgumentException 当参数验证失败时抛出
     */
    @Override
    public ValidationResult executeRules(Object entity, String ruleType) {
        log.info("开始执行业务规则，规则类型: {}", ruleType);
        
        ValidationResult result = ValidationResult.builder()
                .isValid(true)
                .build();
        
        try {
            // 参数验证
            if (entity == null) {
                String errorMsg = "实体对象为空";
                log.warn(errorMsg);
                result.addGlobalError(errorMsg);
                return result;
            }
            
            if (ruleType == null || ruleType.trim().isEmpty()) {
                String errorCode = ErrorCodeConstants.UNSUPPORTED_RULE_TYPE;
                String errorMsg = "规则类型为空";
                log.warn(errorMsg);
                result.addGlobalError(errorMsg, errorCode);
                return result;
            }
            
            // 根据规则类型执行相应的规则
            switch (ruleType) {
                case RuleTypeConstants.HIGH_VALUE_ORDER:
                    if (entity instanceof Map) {
                        log.debug("执行高价值订单规则");
                        return evaluateHighValueOrderRule((Map<String, Object>) entity);
                    } else {
                        String errorCode = ErrorCodeConstants.INVALID_ENTITY_TYPE;
                        String errorMsg = "实体类型不匹配，需要Map类型";
                        log.warn(errorMsg);
                        result.addGlobalError(errorMsg, errorCode);
                    }
                    break;
                case RuleTypeConstants.ORDER_PRIORITY:
                    if (entity instanceof Map) {
                        log.debug("执行订单优先级规则");
                        return evaluateOrderPriorityRule((Map<String, Object>) entity);
                    } else {
                        String errorCode = ErrorCodeConstants.INVALID_ENTITY_TYPE;
                        String errorMsg = "实体类型不匹配，需要Map类型";
                        log.warn(errorMsg);
                        result.addGlobalError(errorMsg, errorCode);
                    }
                    break;
                default:
                    String errorCode = ErrorCodeConstants.UNSUPPORTED_RULE_TYPE;
                    String errorMsg = "不支持的规则类型: " + ruleType;
                    log.warn(errorMsg);
                    result.addGlobalError(errorMsg, errorCode);
            }
        } catch (IllegalArgumentException e) {
            log.error("执行业务规则参数验证失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            String errorCode = ErrorCodeConstants.RULE_EXECUTION_FAILED;
            String errorMsg = "规则执行失败: " + e.getMessage();
            log.error("执行业务规则异常", e);
            result.addGlobalError(errorMsg, errorCode);
        }
        
        log.info("业务规则执行完成，规则类型: {}, 结果: {}", ruleType, result.isValid() ? "有效" : "无效");
        return result;
    }
}