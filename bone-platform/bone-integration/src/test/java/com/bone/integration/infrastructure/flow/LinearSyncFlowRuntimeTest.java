package com.bone.integration.infrastructure.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.integration.application.support.ConnectorSupport;
import com.bone.integration.application.support.FlowSupport;
import com.bone.integration.domain.model.connector.Connector;
import com.bone.integration.domain.model.connector.valueobject.ConnectorType;
import com.bone.integration.domain.model.execution.IntegrationLog;
import com.bone.integration.domain.model.execution.valueobject.ExecutionStatus;
import com.bone.integration.domain.model.flow.FlowConnection;
import com.bone.integration.domain.model.flow.FlowNode;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.model.flow.valueobject.NodeType;
import com.bone.integration.domain.repository.ConnectorRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinearSyncFlowRuntimeTest {

  @Mock private FlowSupport flowSupport;

  @Mock private ConnectorRepository connectorRepository;

  @Mock private ConnectorSupport connectorSupport;

  private LinearSyncFlowRuntime linearSyncFlowRuntime;

  @org.junit.jupiter.api.BeforeEach
  void initRuntime() {
    linearSyncFlowRuntime =
        new LinearSyncFlowRuntime(
            flowSupport, new FlowNodeExecutor(connectorRepository, connectorSupport));
  }

  @Test
  void execute_completesLinearHttpFlow() {
    IntegrationFlow flow = IntegrationFlow.create(1L, "demo", "d");
    flow.activate();
    IntegrationLog log = IntegrationLog.create(10L, 1L, "{}");

    FlowNode start = FlowNode.create(100L, 1L, "start", NodeType.START, Map.of(), 0, 0);
    FlowNode http =
        FlowNode.create(
            101L, 1L, "call", NodeType.HTTP, Map.of("connectorId", 5L, "path", "/api"), 1, 0);
    FlowNode end = FlowNode.create(102L, 1L, "end", NodeType.END, Map.of(), 2, 0);
    FlowConnection c1 = FlowConnection.create(201L, 1L, 100L, 101L, null);
    FlowConnection c2 = FlowConnection.create(202L, 1L, 101L, 102L, null);

    Connector connector =
        Connector.create(5L, "rest", ConnectorType.HTTP, Map.of("url", "http://localhost"));
    when(flowSupport.getFlowNodes(1L)).thenReturn(List.of(start, http, end));
    when(flowSupport.getFlowConnections(1L)).thenReturn(List.of(c1, c2));
    when(connectorRepository.findById(5L)).thenReturn(connector);
    when(connectorSupport.executeConnector(eq(connector), eq("/api"), any()))
        .thenReturn(Map.of("statusCode", 200));

    linearSyncFlowRuntime.execute(log, flow);

    assertEquals(ExecutionStatus.SUCCESS, log.getStatus());
    verify(connectorSupport).executeConnector(eq(connector), eq("/api"), any());
  }
}
