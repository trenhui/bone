package com.bone.engine.extension.studio.domain.store;

import com.bone.engine.extension.studio.domain.model.PluginExecutionLog;
import java.util.List;

public interface PluginExecutionLogStore {

    PluginExecutionLog save(PluginExecutionLog log);

    List<PluginExecutionLog> findAll();

    List<PluginExecutionLog> findByPluginId(Long pluginId);

    List<PluginExecutionLog> findByStatus(String status);

    long count();

    long countByStatus(String status);
}
