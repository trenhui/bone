package com.bone.integration.infrastructure.camel;

import com.bone.integration.domain.flow.FlowConnection;
import com.bone.integration.domain.flow.FlowNode;
import com.bone.integration.domain.flow.IntegrationFlow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 将 {@code int_flow_node} 编译为 Camel 路由（INT-11 占位）。
 *
 * <p>legacy {@code bone-engine/bone-integration} 的图访问器已移除；完整 Choice/并行编排待按平台节点模型重写。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CamelFlowCompiler {

    private final CamelIntegrationContext camelIntegrationContext;

    public boolean isReady() {
        return camelIntegrationContext.getCamelContext().isStarted();
    }

    public void compilePlaceholder(IntegrationFlow flow, List<FlowNode> nodes, List<FlowConnection> connections) {
        log.debug(
                "CamelFlowCompiler placeholder: flowId={}, nodes={}, connections={} (INT-11)",
                flow.getId(),
                nodes.size(),
                connections.size());
    }
}
