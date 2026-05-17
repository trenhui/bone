package com.bone.engine.extension.studio.infrastructure.persistence;

import com.bone.engine.extension.studio.domain.model.PluginExecutionLog;
import com.bone.engine.extension.studio.domain.store.PluginExecutionLogStore;
import com.bone.engine.extension.studio.infrastructure.persistence.converter.StudioPersistenceConverter;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioPluginExecutionLog;
import com.bone.engine.extension.studio.infrastructure.persistence.repository.ExtStudioPluginExecutionLogRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@ConditionalOnProperty(prefix = "bone.extension.studio.persistence", name = "mode", havingValue = "metadata")
public class MetadataPluginExecutionLogStore implements PluginExecutionLogStore {

    private final ExtStudioPluginExecutionLogRepository repository;

    public MetadataPluginExecutionLogStore(ExtStudioPluginExecutionLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public PluginExecutionLog save(PluginExecutionLog log) {
        ExtStudioPluginExecutionLog row = StudioPersistenceConverter.toExecutionLogEntity(log);
        if (row.getId() == null) {
            repository.insert(row);
        } else if (repository.findById(row.getId()) != null) {
            repository.update(row);
        } else {
            repository.insert(row);
        }
        log.setId(row.getId());
        return log;
    }

    @Override
    public List<PluginExecutionLog> findAll() {
        return repository.findByCriteria(
                        Criteria.<ExtStudioPluginExecutionLog>create()
                                .orderByDesc(ExtStudioPluginExecutionLog::getCreatedAt))
                .stream()
                .map(StudioPersistenceConverter::toExecutionLogDomain)
                .sorted(Comparator.comparing(PluginExecutionLog::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<PluginExecutionLog> findByPluginId(Long pluginId) {
        if (pluginId == null) {
            return List.of();
        }
        Criteria<ExtStudioPluginExecutionLog> criteria = Criteria.<ExtStudioPluginExecutionLog>create()
                .eq(ExtStudioPluginExecutionLog::getPluginId, pluginId)
                .orderByDesc(ExtStudioPluginExecutionLog::getCreatedAt);
        return repository.findByCriteria(criteria).stream()
                .map(StudioPersistenceConverter::toExecutionLogDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<PluginExecutionLog> findByStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return findAll();
        }
        Criteria<ExtStudioPluginExecutionLog> criteria = Criteria.<ExtStudioPluginExecutionLog>create()
                .eq(ExtStudioPluginExecutionLog::getStatus, status.trim())
                .orderByDesc(ExtStudioPluginExecutionLog::getCreatedAt);
        return repository.findByCriteria(criteria).stream()
                .map(StudioPersistenceConverter::toExecutionLogDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return repository.countByCriteria(Criteria.<ExtStudioPluginExecutionLog>create());
    }

    @Override
    public long countByStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return count();
        }
        return repository.countByCriteria(Criteria.<ExtStudioPluginExecutionLog>create()
                .eq(ExtStudioPluginExecutionLog::getStatus, status.trim()));
    }
}
