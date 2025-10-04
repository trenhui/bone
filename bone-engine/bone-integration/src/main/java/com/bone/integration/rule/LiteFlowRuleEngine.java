//package com.bone.lowcode.integration.rule;
//
//import com.bone.lowcode.integration.config.InterfaceConfig;
//import com.yomahub.liteflow.slot.Slot;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import com.yomahub.liteflow.core.FlowExecutor;
//import com.yomahub.liteflow.flow.LiteflowResponse;
//import com.yomahub.liteflow.exception.LiteFlowException;
//
///**
// * LiteFlow 规则引擎实现类。
// */
//@Service
//public class LiteFlowRuleEngine implements RuleEngine {
//
//    private static final Logger logger = LoggerFactory.getLogger(LiteFlowRuleEngine.class);
//
//    private final FlowExecutor flowExecutor;
//
//    @Autowired
//    public LiteFlowRuleEngine(FlowExecutor flowExecutor) {
//        this.flowExecutor = flowExecutor;
//    }
//
//    @Override
//    public Object executeRules(String flowId, InterfaceConfig interfaceConfig, Object inputData) {
//        logger.info("Executing rules for flow: {} with input data: {}", flowId, inputData);
//        try {
//            // 执行指定的 LiteFlow 规则链，并返回响应
//            //LiteflowResponse response = flowExecutor.execute2Resp(flowId, data,interfaceConfig);
//
//            // 执行 LiteFlow 规则链，传递 interfaceConfig 和 inputData 到 Slot
//            // Set interfaceConfig and inputData in the slot before executing the flow
//            LiteflowResponse response = flowExecutor.execute2Resp(flowId, inputData,interfaceConfig);
//
//            // After executing the flow, check if it was successful
//            if (response.isSuccess()) {
//                Slot slot = response.getSlot();
//                // Manually set the additional data to the slot after execution (if needed)
//               // slot.setInput("interfaceConfig", interfaceConfig);  // 设置 interfaceConfig
//
//                logger.info("Successfully executed rules for flow: {}", flowId);
//                return slot.getResponseData();  // 返回处理后的数据
//            } else {
//                logger.error("Failed to execute LiteFlow rules: {}", response.getMessage());
//                throw new RuntimeException("LiteFlow execution failed");
//            }
//        } catch (LiteFlowException e) {
//            logger.error("Error executing rules for flow: {} with data: {}", flowId, inputData, e);
//            throw new RuntimeException("Error executing rules with LiteFlow", e);
//        }
//    }
//}