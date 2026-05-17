package com.bone.engine.extension.studio.domain.store;

import com.bone.engine.extension.studio.domain.model.PluginVersion;
import java.util.List;
import org.springframework.lang.Nullable;

public interface PluginVersionStore {

    List<PluginVersion> findByPluginId(Long pluginId);

    @Nullable
    PluginVersion findByPluginIdAndVersion(Long pluginId, String version);

    @Nullable
    PluginVersion findActiveByPluginId(Long pluginId);

    PluginVersion save(PluginVersion version);

    boolean deleteById(Long id);

    void deleteByPluginId(Long pluginId);
}
