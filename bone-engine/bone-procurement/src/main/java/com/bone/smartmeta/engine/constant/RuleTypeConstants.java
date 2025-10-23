package com.bone.smartmeta.engine.constant;

/**
 * 规则类型常量类
 * 统一管理业务规则引擎支持的规则类型
 */
public final class RuleTypeConstants {
    
    // 私有构造函数，防止实例化
    private RuleTypeConstants() {
        throw new AssertionError("不能实例化RuleTypeConstants类");
    }
    
    /**
     * 高价值订单规则
     * 用于评估订单是否为高价值订单，需要特殊审批流程
     */
    public static final String HIGH_VALUE_ORDER = "HIGH_VALUE_ORDER";
    
    /**
     * 订单优先级规则
     * 用于评估和设置订单的优先级
     */
    public static final String ORDER_PRIORITY = "ORDER_PRIORITY";
}