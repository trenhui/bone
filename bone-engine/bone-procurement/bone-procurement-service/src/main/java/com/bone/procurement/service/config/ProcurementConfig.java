package com.bone.procurement.service.config;

import com.bone.procurement.engine.rules.DefaultPurchaseOrderRuleEngine;
import com.bone.procurement.engine.rules.PurchaseOrderRuleEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 采购模块配置类
 * 配置采购模块的Bean定义和依赖关系
 */
@Configuration
@EnableTransactionManagement
public class ProcurementConfig {
    
    /**
     * 配置采购订单规则引擎Bean
     * @return 采购订单规则引擎实例
     */
    @Bean
    public PurchaseOrderRuleEngine purchaseOrderRuleEngine() {
        return new DefaultPurchaseOrderRuleEngine();
    }
    
    /**
     * 配置采购模块的常量定义
     * @return 采购常量实例
     */
    @Bean
    public ProcurementConstants procurementConstants() {
        return new ProcurementConstants();
    }
}