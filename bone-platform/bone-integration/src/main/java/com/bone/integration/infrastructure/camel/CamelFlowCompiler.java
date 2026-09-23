package com.bone.integration.infrastructure.camel;

import static org.apache.camel.builder.Builder.simple;

import com.bone.core.exception.DomainException;
import com.bone.integration.domain.model.flow.FlowConnection;
import com.bone.integration.domain.model.flow.FlowNode;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.model.flow.valueobject.NodeType;
import com.bone.integration.infrastructure.flow.FlowNodeExecutor;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.springframework.stereotype.Component;

/**
 * 将 {@code int_flow_node} 编译为 Camel 路由（INT-11）。
 *
 * <p>支持线性拓扑、{@link NodeType#DECISION} 分支（Camel {@code choice}）与 {@link NodeType#PARALLEL} 扇出（{@code
 * multicast}）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CamelFlowCompiler {

  static final String ROUTE_PREFIX = "bone-integration-flow-";
  static final String ENDPOINT_PREFIX = "direct:bone-integration-flow-";

  private final CamelIntegrationContext camelIntegrationContext;
  private final FlowNodeExecutor flowNodeExecutor;

  public CamelContext getCamelContext() {
    return camelIntegrationContext.getCamelContext();
  }

  public boolean isReady() {
    return getCamelContext().isStarted();
  }

  public String endpointUri(Long flowId) {
    return ENDPOINT_PREFIX + flowId;
  }

  public String routeId(Long flowId) {
    return ROUTE_PREFIX + flowId;
  }

  public void compile(IntegrationFlow flow, List<FlowNode> nodes, List<FlowConnection> connections)
      throws Exception {
    Objects.requireNonNull(flow, "flow");
    if (!isReady()) {
      throw new DomainException("Camel 上下文未启动，无法编译流程");
    }
    FlowGraph graph = FlowGraph.of(nodes, connections);
    FlowNode start = graph.findStartNode();
    CamelContext camelContext = getCamelContext();
    String routeId = routeId(flow.getId());
    String endpoint = endpointUri(flow.getId());

    removeRouteIfPresent(camelContext, routeId);

    camelContext.addRoutes(
        new RouteBuilder() {
          @Override
          public void configure() {
            RouteDefinition route = from(endpoint).routeId(routeId);
            appendFromNode(route, start, graph);
          }
        });
    log.info("Compiled Camel route flowId={} routeId={}", flow.getId(), routeId);
  }

  public void removeRoute(Long flowId) {
    removeRouteIfPresent(camelIntegrationContext.getCamelContext(), routeId(flowId));
  }

  private void removeRouteIfPresent(CamelContext camelContext, String routeId) {
    if (camelContext.getRoute(routeId) != null) {
      try {
        camelContext.getRouteController().stopRoute(routeId);
      } catch (Exception ex) {
        log.debug("Stop route {} before remove: {}", routeId, ex.getMessage());
      }
      try {
        camelContext.removeRoute(routeId);
      } catch (Exception ex) {
        log.warn("Remove route {} failed: {}", routeId, ex.getMessage());
      }
    }
  }

  private void appendFromNode(ProcessorDefinition<?> route, FlowNode node, FlowGraph graph) {
    if (graph.isTerminal(node)) {
      return;
    }
    switch (node.getType()) {
      case START, LOG, WAIT, TRANSFORM -> {}
      case HTTP -> route.process(
          exchange ->
              exchange.getIn().setBody(flowNodeExecutor.execute(node, exchange.getIn().getBody())));
      case DECISION -> appendDecision(route, node, graph);
      case PARALLEL -> appendParallel(route, node, graph);
      default -> throw new DomainException("Camel 编译暂不支持节点类型: " + node.getType());
    }

    if (node.getType() == NodeType.DECISION || node.getType() == NodeType.PARALLEL) {
      return;
    }

    FlowNode next = graph.singleNext(node);
    if (next != null) {
      appendFromNode(route, next, graph);
    }
  }

  private void appendDecision(ProcessorDefinition<?> route, FlowNode node, FlowGraph graph) {
    List<FlowConnection> links = graph.outgoing(node);
    ChoiceDefinition choice = route.choice();
    boolean hasOtherwise = false;
    for (FlowConnection link : links) {
      String whenExpr = normalizeWhen(link.getCondition());
      FlowNode target = graph.requireNode(link.getTargetNodeId());
      if (whenExpr == null) {
        if (!hasOtherwise) {
          ProcessorDefinition<?> branch = choice.otherwise();
          appendFromNode(branch, target, graph);
          hasOtherwise = true;
        }
      } else {
        ProcessorDefinition<?> branch = choice.when(simple(whenExpr));
        appendFromNode(branch, target, graph);
      }
    }
    if (!hasOtherwise && !links.isEmpty()) {
      FlowNode fallback = graph.requireNode(links.get(0).getTargetNodeId());
      ProcessorDefinition<?> branch = choice.otherwise();
      appendFromNode(branch, fallback, graph);
    }
    choice.end();
  }

  private void appendParallel(ProcessorDefinition<?> route, FlowNode node, FlowGraph graph) {
    List<FlowConnection> links = graph.outgoing(node);
    route.process(
        exchange -> {
          Object body = exchange.getIn().getBody();
          java.util.List<Object> results = new java.util.ArrayList<>();
          for (FlowConnection link : links) {
            FlowNode target = graph.requireNode(link.getTargetNodeId());
            results.add(runLinearChain(target, body, graph));
          }
          exchange.getIn().setBody(results);
        });
    FlowNode mergeNext = findMergeAfterParallel(node, graph);
    if (mergeNext != null) {
      appendFromNode(route, mergeNext, graph);
    }
  }

  private Object runLinearChain(FlowNode start, Object context, FlowGraph graph) {
    FlowNode current = start;
    int guard = 64;
    while (current != null && !graph.isTerminal(current) && guard-- > 0) {
      if (current.getType() == NodeType.DECISION) {
        context = runNestedDecision(current, context, graph);
        current = resolveDecisionNext(current, context, graph);
      } else if (current.getType() == NodeType.PARALLEL) {
        context = runNestedParallel(current, context, graph);
        current = findMergeAfterParallel(current, graph);
      } else {
        context = flowNodeExecutor.execute(current, context);
        current = graph.singleNext(current);
      }
    }
    return context;
  }

  /** 嵌套 DECISION：同步执行命中分支（递归支持嵌套）。 */
  private Object runNestedDecision(FlowNode node, Object context, FlowGraph graph) {
    Object result = context;
    for (FlowConnection link : graph.outgoing(node)) {
      String whenExpr = normalizeWhen(link.getCondition());
      if (whenExpr == null || evaluateCondition(whenExpr, context)) {
        result = runLinearChain(graph.requireNode(link.getTargetNodeId()), context, graph);
        break;
      }
    }
    return result;
  }

  /** 嵌套 PARALLEL：同步执行各分支并汇聚结果。 */
  private Object runNestedParallel(FlowNode node, Object context, FlowGraph graph) {
    java.util.List<Object> results = new java.util.ArrayList<>();
    for (FlowConnection link : graph.outgoing(node)) {
      FlowNode target = graph.requireNode(link.getTargetNodeId());
      results.add(runLinearChain(target, context, graph));
    }
    return results;
  }

  /** 评估 Simple 条件表达式（默认按 body 字符串相等兜底）。 */
  private boolean evaluateCondition(String normalizedExpr, Object body) {
    try {
      Object result =
          getCamelContext()
              .resolveLanguage("simple")
              .createExpression(normalizedExpr)
              .evaluate(
                  new org.apache.camel.support.DefaultExchange(getCamelContext()), Object.class);
      return Boolean.TRUE.equals(result);
    } catch (Exception ex) {
      // 兜底：按 body 字符串相等比较
      String bodyStr = body == null ? "" : String.valueOf(body);
      String expected = extractLiteral(normalizedExpr);
      return expected != null && bodyStr.equals(expected);
    }
  }

  private String extractLiteral(String normalizedExpr) {
    int idx = normalizedExpr.lastIndexOf('\'');
    if (idx > 0 && normalizedExpr.charAt(idx - 1) == '\\') {
      return null;
    }
    int start = normalizedExpr.indexOf('\'');
    int end = normalizedExpr.lastIndexOf('\'');
    if (start >= 0 && end > start) {
      return normalizedExpr.substring(start + 1, end);
    }
    return null;
  }

  /** 选择 DECISION 命中分支；无命中取无条件 otherwise 分支，再兜底 singleNext。 */
  private FlowNode resolveDecisionNext(FlowNode node, Object context, FlowGraph graph) {
    for (FlowConnection link : graph.outgoing(node)) {
      String whenExpr = normalizeWhen(link.getCondition());
      if (whenExpr != null && evaluateCondition(whenExpr, context)) {
        return graph.requireNode(link.getTargetNodeId());
      }
    }
    for (FlowConnection link : graph.outgoing(node)) {
      if (link.getCondition() == null || link.getCondition().isBlank()) {
        return graph.requireNode(link.getTargetNodeId());
      }
    }
    return graph.singleNext(node);
  }

  /** PARALLEL 之后若各分支汇聚到同一节点，则继续主编排。 */
  private FlowNode findMergeAfterParallel(FlowNode parallelNode, FlowGraph graph) {
    List<FlowConnection> links = graph.outgoing(parallelNode);
    if (links.isEmpty()) {
      return null;
    }
    Long mergeId = null;
    for (FlowConnection link : links) {
      FlowNode target = graph.requireNode(link.getTargetNodeId());
      FlowNode afterBranch = walkToMergeCandidate(target, graph);
      if (afterBranch == null) {
        return null;
      }
      if (mergeId == null) {
        mergeId = afterBranch.getId();
      } else if (!mergeId.equals(afterBranch.getId())) {
        return null;
      }
    }
    return mergeId != null ? graph.requireNode(mergeId) : null;
  }

  private FlowNode walkToMergeCandidate(FlowNode node, FlowGraph graph) {
    if (graph.isTerminal(node)) {
      return null;
    }
    try {
      return graph.singleNext(node);
    } catch (DomainException ex) {
      return null;
    }
  }

  /** 将连线条件规范为 Camel Simple（空则走 otherwise）。 */
  private static String normalizeWhen(String condition) {
    if (condition == null || condition.isBlank()) {
      return null;
    }
    String trimmed = condition.trim();
    if (trimmed.startsWith("${") || trimmed.startsWith("$")) {
      return trimmed;
    }
    return "${body} == '" + trimmed.replace("'", "\\'") + "'";
  }
}
