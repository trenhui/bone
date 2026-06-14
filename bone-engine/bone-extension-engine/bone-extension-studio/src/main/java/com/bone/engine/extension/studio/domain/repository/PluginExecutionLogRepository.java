package com.bone.engine.extension.studio.domain.repository;

import com.bone.engine.extension.studio.domain.model.PluginExecutionLog;

/**
 * 执行日志写侧仓储；读见 {@link com.bone.engine.extension.studio.domain.gateway.PluginExecutionLogReadPort}。
 */
public interface PluginExecutionLogRepository {

  PluginExecutionLog save(PluginExecutionLog log);
}
