package com.bone.integration.application.port;

import com.bone.integration.domain.flow.FlowConnection;
import com.bone.integration.domain.flow.FlowNode;
import com.bone.integration.domain.flow.IntegrationFlow;
import java.util.List;

/**
 * Camel 流程编译与执行出站端口（应用层定义，infrastructure 实现）。
 *
 * <p>隔离 {@code org.apache.camel}，避免 application 依赖 infrastructure 中的 Camel 类型。
 */
public interface CamelFlowExecutionPort {

  boolean isReady();

  String endpointUri(Long flowId);

  void compile(IntegrationFlow flow, List<FlowNode> nodes, List<FlowConnection> connections)
      throws Exception;

  /** 在已编译的 direct endpoint 上同步执行并返回 body。 */
  Object executeOnEndpoint(String endpointUri, Object input) throws Exception;
}
