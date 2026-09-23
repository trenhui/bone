package com.bone.integration.infrastructure.flow;

import com.bone.core.exception.DomainException;
import com.bone.integration.application.support.ConnectorSupport;
import com.bone.integration.domain.model.connector.Connector;
import com.bone.integration.domain.model.flow.FlowNode;
import com.bone.integration.domain.repository.ConnectorRepository;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 单节点执行（INT-09 线性运行时与 INT-11 Camel 路由共用）。 */
@Component
@RequiredArgsConstructor
public class FlowNodeExecutor {

  private final ConnectorRepository connectorRepository;
  private final ConnectorSupport connectorSupport;

  public Object execute(FlowNode node, Object context) {
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

    return connectorSupport.executeConnector(connector, endpoint != null ? endpoint : "", params);
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
