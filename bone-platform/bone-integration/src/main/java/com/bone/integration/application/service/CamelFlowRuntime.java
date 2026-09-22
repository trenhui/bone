package com.bone.integration.application.service;

import com.bone.core.exception.DomainException;
import com.bone.integration.application.port.CamelFlowExecutionPort;
import com.bone.integration.domain.model.execution.IntegrationLog;
import com.bone.integration.domain.model.flow.FlowConnection;
import com.bone.integration.domain.model.flow.FlowNode;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** Camel 编译路由执行（INT-11，需 {@code integration.camel.execution-enabled=true}）。 */
@Service
@Primary
@ConditionalOnProperty(
    prefix = "integration.camel",
    name = "execution-enabled",
    havingValue = "true")
@RequiredArgsConstructor
public class CamelFlowRuntime implements FlowRuntime {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final FlowService flowService;
  private final CamelFlowExecutionPort camelFlowExecution;

  @Override
  public void execute(IntegrationLog log, IntegrationFlow flow) {
    log.start();
    List<FlowNode> nodes = flowService.getFlowNodes(flow.getId());
    List<FlowConnection> connections = flowService.getFlowConnections(flow.getId());

    try {
      if (!camelFlowExecution.isReady()) {
        throw new DomainException("Camel 运行时未就绪");
      }
      try {
        camelFlowExecution.compile(flow, nodes, connections);
      } catch (Exception compileEx) {
        throw new DomainException("流程 Camel 编译失败: " + compileEx.getMessage());
      }
      Object input = parseInput(log.getInputData());
      Object result =
          camelFlowExecution.executeOnEndpoint(camelFlowExecution.endpointUri(flow.getId()), input);
      log.complete(stringify(result));
    } catch (DomainException ex) {
      log.fail(ex.getMessage());
    } catch (Exception ex) {
      log.fail(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
    }
  }

  private static Object parseInput(String inputData) {
    if (inputData == null || inputData.isBlank()) {
      return Map.of();
    }
    try {
      return MAPPER.readValue(inputData, new TypeReference<Map<String, Object>>() {});
    } catch (Exception ex) {
      return inputData;
    }
  }

  private static String stringify(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String s) {
      return s;
    }
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception ex) {
      return String.valueOf(value);
    }
  }
}
