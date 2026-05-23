package com.bone.integration.infrastructure.camel;

import com.bone.integration.application.port.CamelFlowExecutionPort;
import com.bone.integration.domain.flow.FlowConnection;
import com.bone.integration.domain.flow.FlowNode;
import com.bone.integration.domain.flow.IntegrationFlow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;

/** {@link CamelFlowExecutionPort} 的 Camel 实现。 */
@Component
@RequiredArgsConstructor
public class CamelFlowExecutionAdapter implements CamelFlowExecutionPort {

    private final CamelFlowCompiler camelFlowCompiler;

    @Override
    public boolean isReady() {
        return camelFlowCompiler.isReady();
    }

    @Override
    public String endpointUri(Long flowId) {
        return camelFlowCompiler.endpointUri(flowId);
    }

    @Override
    public void compile(IntegrationFlow flow, List<FlowNode> nodes, List<FlowConnection> connections)
            throws Exception {
        camelFlowCompiler.compile(flow, nodes, connections);
    }

    @Override
    public Object executeOnEndpoint(String endpointUri, Object input) throws Exception {
        try (ProducerTemplate template = camelFlowCompiler.getCamelContext().createProducerTemplate()) {
            return template.requestBody(endpointUri, input);
        }
    }
}
