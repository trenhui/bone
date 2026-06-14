package com.bone.engine.extension.studio.domain.gateway;

import com.bone.engine.extension.studio.domain.model.PluginExecutionLog;
import java.util.List;

/** 插件执行日志读侧端口（ADR-0013）。 */
public interface PluginExecutionLogReadPort {

  List<PluginExecutionLog> findAll();

  List<PluginExecutionLog> findByPluginId(Long pluginId);

  List<PluginExecutionLog> findByStatus(String status);

  long count();

  long countByStatus(String status);
}
