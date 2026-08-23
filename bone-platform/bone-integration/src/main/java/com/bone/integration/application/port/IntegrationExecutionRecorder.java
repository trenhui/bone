package com.bone.integration.application.port;

import com.bone.integration.domain.execution.IntegrationLog;

/** 集成执行可观测性出站端口（Micrometer 等由 infrastructure 实现）。 */
public interface IntegrationExecutionRecorder {

  void record(IntegrationLog log);

  /** 记录连接器连通性测试结果（default 空实现：非强制能力）。 */
  default void recordConnectorTest(String connectorType, boolean success) {
    // 默认不记录，由具体实现按需覆盖
  }
}
