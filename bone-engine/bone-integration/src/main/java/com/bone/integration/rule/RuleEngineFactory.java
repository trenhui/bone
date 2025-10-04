//package com.bone.lowcode.integration.rule;
//
//import org.springframework.stereotype.Component;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//
///**
// * 规则引擎工厂类，默认返回 LiteFlow 规则引擎。
// */
//@Component
//public class RuleEngineFactory {
//
//    private static final Logger logger = LoggerFactory.getLogger(RuleEngineFactory.class);
//
//    private final LiteFlowRuleEngine liteFlowRuleEngine;
//
//    @Autowired
//    public RuleEngineFactory(LiteFlowRuleEngine liteFlowRuleEngine) {
//        this.liteFlowRuleEngine = liteFlowRuleEngine;
//    }
//
//    /**
//     * 获取默认规则引擎。
//     * @return 规则引擎实例
//     */
//    public RuleEngine getDefaultRuleEngine() {
//        logger.info("Returning default LiteFlow rule engine");
//        return liteFlowRuleEngine;
//    }
//}