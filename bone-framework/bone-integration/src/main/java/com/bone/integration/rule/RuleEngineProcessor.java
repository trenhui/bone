//package com.bone.lowcode.integration.rule;
//
//import com.bone.lowcode.integration.config.InterfaceConfig;
//import org.apache.camel.Exchange;
//import org.apache.camel.Processor;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
///**
// * 使用规则引擎处理业务逻辑。
// */
//@Component
//public class RuleEngineProcessor implements Processor {
//
//    private static final Logger logger = LoggerFactory.getLogger(RuleEngineProcessor.class);
//
//    private final RuleEngineFactory ruleEngineFactory;
//
//    @Autowired
//    public RuleEngineProcessor(RuleEngineFactory ruleEngineFactory) {
//        this.ruleEngineFactory = ruleEngineFactory;
//    }
//
//    @Override
//    public void process(Exchange exchange) throws Exception {
//        // 获取输入数据
//        Object inputData = exchange.getIn().getBody(String.class);
//
//        // 动态获取 flowId
//        String flowId = exchange.getIn().getHeader("flowId", "dataConversionFlow", String.class);
//        InterfaceConfig interfaceConfig=exchange.getIn().getHeader("interfaceConfig", InterfaceConfig.class);
//        // 获取默认的规则引擎实例（LiteFlow）
//        RuleEngine ruleEngine = ruleEngineFactory.getDefaultRuleEngine();
//
//        // 调用规则引擎执行规则，处理数据
//        logger.info("Processing flow: {} with input data", flowId);
//        Object processedData = ruleEngine.executeRules(flowId, interfaceConfig,inputData);
//
//        // 将处理后的数据存入 Camel Exchange 中
//        exchange.getIn().setBody(processedData);
//
//    }
//}