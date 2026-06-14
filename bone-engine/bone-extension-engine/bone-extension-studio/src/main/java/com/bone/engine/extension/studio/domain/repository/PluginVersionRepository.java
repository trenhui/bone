package com.bone.engine.extension.studio.domain.repository;

import com.bone.engine.extension.studio.domain.model.PluginVersion;
import org.springframework.lang.Nullable;

/** 插件版本写侧仓储；列表读见 {@link com.bone.engine.extension.studio.domain.gateway.PluginVersionReadPort}。 */
public interface PluginVersionRepository {

  @Nullable
  PluginVersion findByPluginVersion(Long pluginId, String version);

  PluginVersion save(PluginVersion version);

  boolean remove(Long id);
}
