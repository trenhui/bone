package com.bone.engine.extension.studio.domain.gateway;

import com.bone.engine.extension.studio.domain.model.PluginVersion;
import java.util.List;

/** 插件版本读侧端口（ADR-0013）。 */
public interface PluginVersionReadPort {

    List<PluginVersion> findByPluginId(Long pluginId);

    /** 当前激活版本（单插件至多一个 active）。 */
    @org.springframework.lang.Nullable
    PluginVersion findActiveByPluginId(Long pluginId);
}
