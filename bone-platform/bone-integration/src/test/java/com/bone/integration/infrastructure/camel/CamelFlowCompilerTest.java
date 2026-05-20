package com.bone.integration.infrastructure.camel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.integration.application.service.FlowNodeExecutor;
import com.bone.integration.domain.connector.Connector;
import com.bone.integration.domain.flow.FlowConnection;
import com.bone.integration.domain.flow.FlowNode;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.integration.domain.model.connector.vo.ConnectorType;
import com.bone.integration.domain.model.flow.vo.NodeType;
import com.bone.integration.domain.repository.ConnectorRepository;
import com.bone.integration.domain.service.ConnectorService;
import java.util.List;
import java.util.Map;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CamelFlowCompilerTest {

    @Mock
    private ConnectorRepository connectorRepository;

    @Mock
    private ConnectorService connectorService;

    private DefaultCamelContext camelContext;
    private CamelFlowCompiler compiler;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();
        CamelIntegrationContext integrationContext = mock(CamelIntegrationContext.class);
        when(integrationContext.getCamelContext()).thenReturn(camelContext);
        FlowNodeExecutor executor = new FlowNodeExecutor(connectorRepository, connectorService);
        compiler = new CamelFlowCompiler(integrationContext, executor);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    void compilesAndExecutesLinearFlow() throws Exception {
        IntegrationFlow flow = IntegrationFlow.create(1L, "demo", "d");
        FlowNode start = FlowNode.create(100L, 1L, "start", NodeType.START, Map.of(), 0, 0);
        FlowNode http =
                FlowNode.create(101L, 1L, "call", NodeType.HTTP, Map.of("connectorId", 5L, "path", "/api"), 1, 0);
        FlowNode end = FlowNode.create(102L, 1L, "end", NodeType.END, Map.of(), 2, 0);
        FlowConnection c1 = FlowConnection.create(201L, 1L, 100L, 101L, null);
        FlowConnection c2 = FlowConnection.create(202L, 1L, 101L, 102L, null);

        Connector connector = Connector.create(5L, "rest", ConnectorType.HTTP, Map.of("url", "http://localhost"));
        when(connectorRepository.findById(5L)).thenReturn(connector);
        when(connectorService.executeConnector(connector, "/api", Map.of("k", "v")))
                .thenReturn(Map.of("statusCode", 200));

        compiler.compile(flow, List.of(start, http, end), List.of(c1, c2));

        assertNotNull(camelContext.getRoute(compiler.routeId(1L)));
        Object result =
                camelContext
                        .createProducerTemplate()
                        .requestBody(compiler.endpointUri(1L), Map.of("k", "v"));
        assertEquals(200, ((Map<?, ?>) result).get("statusCode"));
    }

    @Test
    void compilesDecisionBranches() throws Exception {
        IntegrationFlow flow = IntegrationFlow.create(2L, "branch", "d");
        FlowNode start = FlowNode.create(200L, 2L, "start", NodeType.START, Map.of(), 0, 0);
        FlowNode decision = FlowNode.create(201L, 2L, "decide", NodeType.DECISION, Map.of(), 1, 0);
        FlowNode pathA = FlowNode.create(202L, 2L, "pathA", NodeType.LOG, Map.of(), 2, 0);
        FlowNode pathB = FlowNode.create(203L, 2L, "pathB", NodeType.LOG, Map.of(), 3, 0);
        FlowNode end = FlowNode.create(204L, 2L, "end", NodeType.END, Map.of(), 4, 0);
        FlowConnection c1 = FlowConnection.create(301L, 2L, 200L, 201L, null);
        FlowConnection c2 = FlowConnection.create(302L, 2L, 201L, 202L, "A");
        FlowConnection c3 = FlowConnection.create(303L, 2L, 201L, 203L, null);
        FlowConnection c4 = FlowConnection.create(304L, 2L, 202L, 204L, null);
        FlowConnection c5 = FlowConnection.create(305L, 2L, 203L, 204L, null);

        compiler.compile(
                flow, List.of(start, decision, pathA, pathB, end), List.of(c1, c2, c3, c4, c5));

        assertNotNull(camelContext.getRoute(compiler.routeId(2L)));
        Object result =
                camelContext
                        .createProducerTemplate()
                        .requestBody(compiler.endpointUri(2L), "A");
        assertEquals("A", result);
    }
}
