package com.bone.integration.application.port;

import com.bone.integration.domain.execution.IntegrationLog;

/** 集成执行可观测性出站端口（Micrometer 等由 infrastructure 实现）。 */
public interface IntegrationExecutionRecorder {

    void record(IntegrationLog log);
}
