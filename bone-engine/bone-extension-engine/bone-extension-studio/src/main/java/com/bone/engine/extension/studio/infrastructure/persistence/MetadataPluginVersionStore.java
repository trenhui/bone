package com.bone.engine.extension.studio.infrastructure.persistence;

import com.bone.engine.extension.studio.domain.model.PluginVersion;
import com.bone.engine.extension.studio.domain.store.PluginVersionStore;
import com.bone.engine.extension.studio.infrastructure.persistence.converter.StudioPersistenceConverter;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioPluginVersion;
import com.bone.engine.extension.studio.infrastructure.persistence.repository.ExtStudioPluginVersionRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "bone.extension.studio.persistence", name = "mode", havingValue = "metadata")
public class MetadataPluginVersionStore implements PluginVersionStore {

    private final ExtStudioPluginVersionRepository repository;

    public MetadataPluginVersionStore(ExtStudioPluginVersionRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<PluginVersion> findByPluginId(Long pluginId) {
        if (pluginId == null) {
            return List.of();
        }
        Criteria<ExtStudioPluginVersion> criteria = Criteria.<ExtStudioPluginVersion>create()
                .eq(ExtStudioPluginVersion::getPluginId, pluginId)
                .orderByDesc(ExtStudioPluginVersion::getCreatedAt);
        return repository.findByCriteria(criteria).stream()
                .map(StudioPersistenceConverter::toPluginVersionDomain)
                .sorted(Comparator.comparing(PluginVersion::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    @Nullable
    public PluginVersion findByPluginIdAndVersion(Long pluginId, String version) {
        if (pluginId == null || version == null) {
            return null;
        }
        Criteria<ExtStudioPluginVersion> criteria = Criteria.<ExtStudioPluginVersion>create()
                .eq(ExtStudioPluginVersion::getPluginId, pluginId)
                .eq(ExtStudioPluginVersion::getReleaseVersion, version);
        List<ExtStudioPluginVersion> rows = repository.findByCriteria(criteria);
        return rows.isEmpty() ? null : StudioPersistenceConverter.toPluginVersionDomain(rows.get(0));
    }

    @Override
    @Nullable
    public PluginVersion findActiveByPluginId(Long pluginId) {
        if (pluginId == null) {
            return null;
        }
        Criteria<ExtStudioPluginVersion> criteria = Criteria.<ExtStudioPluginVersion>create()
                .eq(ExtStudioPluginVersion::getPluginId, pluginId)
                .eq(ExtStudioPluginVersion::getIsActive, true);
        List<ExtStudioPluginVersion> rows = repository.findByCriteria(criteria);
        return rows.isEmpty() ? null : StudioPersistenceConverter.toPluginVersionDomain(rows.get(0));
    }

    @Override
    public PluginVersion save(PluginVersion version) {
        ExtStudioPluginVersion row = StudioPersistenceConverter.toPluginVersionEntity(version);
        if (row.getId() == null) {
            repository.insert(row);
        } else if (repository.findById(row.getId()) != null) {
            repository.update(row);
        } else {
            repository.insert(row);
        }
        version.setId(row.getId());
        return version;
    }

    @Override
    public boolean deleteById(Long id) {
        return id != null && repository.deleteById(id);
    }

    @Override
    public void deleteByPluginId(Long pluginId) {
        if (pluginId == null) {
            return;
        }
        findByPluginId(pluginId).forEach(v -> deleteById(v.getId()));
    }
}
