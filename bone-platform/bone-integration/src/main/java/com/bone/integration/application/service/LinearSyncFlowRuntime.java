package com.bone.integration.application.service;

import com.bone.core.exception.DomainException;
import com.bone.integration.domain.connector.Connector;
import com.bone.integration.domain.execution.IntegrationLog;
import com.bone.integration.domain.flow.FlowConnection;
import com.bone.integration.domain.flow.FlowNode;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.integration.domain.model.flow.vo.NodeType;
import com.bone.integration.domain.repository.ConnectorRepository;
import com.bone.integration.domain.service.ConnectorService;
import com.bone.integration.domain.service.FlowService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 线性拓扑同步执行（当前默认运行时，INT-09）。 */
@Service
@RequiredArgsConstructor
public class LinearSyncFlowRuntime implements FlowRuntime {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FlowService flowService;
    private final ConnectorRepository connectorRepository;
    private final ConnectorService connectorService;

    @Override
    public void execute(IntegrationLog log, IntegrationFlow flow) {
        log.start();
        List<FlowNode> nodes = flowService.getFlowNodes(flow.getId());
        List<FlowConnection> connections = flowService.getFlowConnections(flow.getId());

        try {
            Object context = parseInput(log.getInputData());
            FlowNode current = findStartNode(nodes);
            int guard = nodes.size() + 2;

            while (current != null && current.getType() != NodeType.END && guard-- > 0) {
                context = executeNode(current, context);
                current = nextNode(current, connections, nodes);
            }

            if (current == null || current.getType() != NodeType.END) {
                log.fail("流程未到达结束节点");
            } else {
                log.complete(stringify(context));
            }
        } catch (DomainException ex) {
            log.fail(ex.getMessage());
        } catch (Exception ex) {
            log.fail(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    private Object executeNode(FlowNode node, Object context) {
        return switch (node.getType()) {
            case START, END, LOG, WAIT -> context;
            case HTTP -> executeHttpNode(node, context);
            case TRANSFORM -> context;
            default -> throw new DomainException("节点类型暂未支持执行: " + node.getType());
        };
    }

    private Object executeHttpNode(FlowNode node, Object context) {
        Map<String, Object> config = node.getConfig();
        Long connectorId = toLong(config.get("connectorId"));
        if (connectorId == null) {
            throw new DomainException("HTTP 节点缺少 connectorId 配置");
        }
        Connector connector = connectorRepository.findById(connectorId);
        if (connector == null) {
            throw new DomainException("连接器不存在: " + connectorId);
        }

        String endpoint = stringVal(config.get("path"));
        if (endpoint == null) {
            endpoint = stringVal(config.get("endpoint"));
        }

        Map<String, Object> params = new HashMap<>();
        if (config.get("method") != null) {
            params.put("method", config.get("method"));
        }
        if (context instanceof Map<?, ?> map) {
            map.forEach((k, v) -> params.put(String.valueOf(k), v));
        } else if (context != null) {
            params.put("body", context);
        }

        return connectorService.executeConnector(connector, endpoint != null ? endpoint : "", params);
    }

    private static FlowNode findStartNode(List<FlowNode> nodes) {
        return nodes.stream()
                .filter(n -> n.getType() == NodeType.START)
                .findFirst()
                .orElseThrow(() -> new DomainException("流程缺少开始节点"));
    }

    private static FlowNode nextNode(FlowNode current, List<FlowConnection> connections, List<FlowNode> nodes) {
        Optional<FlowConnection> link = connections.stream()
                .filter(c -> current.getId().equals(c.getSourceNodeId()))
                .findFirst();
        if (link.isEmpty()) {
            return null;
        }
        Long targetId = link.get().getTargetNodeId();
        return nodes.stream()
                .filter(n -> targetId.equals(n.getId()))
                .findFirst()
                .orElse(null);
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

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
