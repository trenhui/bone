package com.bone.engine.extension.studio.infrastructure.persistence;

import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.store.ExtPointStore;
import com.bone.engine.extension.studio.infrastructure.persistence.converter.StudioPersistenceConverter;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioExtensionPoint;
import com.bone.engine.extension.studio.infrastructure.persistence.repository.ExtStudioExtensionPointRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.Date;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(prefix = "bone.extension.studio.persistence", name = "mode", havingValue = "metadata")
public class MetadataExtPointStore implements ExtPointStore {

    private final ExtStudioExtensionPointRepository repository;

    public MetadataExtPointStore(ExtStudioExtensionPointRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ExtPoint> findAll() {
        return repository.findByCriteria(Criteria.<ExtStudioExtensionPoint>create().orderByDesc(
                        ExtStudioExtensionPoint::getCreatedAt))
                .stream()
                .map(StudioPersistenceConverter::toDomain)
                .sorted(Comparator.comparing(ExtPoint::getId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toList());
    }

    @Override
    @Nullable
    public ExtPoint findById(Long id) {
        ExtStudioExtensionPoint row = repository.findById(id);
        return row == null ? null : StudioPersistenceConverter.toDomain(row);
    }

    @Override
    @Nullable
    public ExtPoint findByInterfaceName(String interfaceName) {
        if (!StringUtils.hasText(interfaceName)) {
            return null;
        }
        Criteria<ExtStudioExtensionPoint> criteria = Criteria.<ExtStudioExtensionPoint>create()
                .eq(ExtStudioExtensionPoint::getInterfaceName, interfaceName.trim());
        List<ExtStudioExtensionPoint> rows = repository.findByCriteria(criteria);
        return rows.isEmpty() ? null : StudioPersistenceConverter.toDomain(rows.get(0));
    }

    @Override
    public List<ExtPoint> search(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return findAll();
        }
        String lower = keyword.trim().toLowerCase();
        return findAll().stream()
                .filter(
                        p -> contains(p.getName(), lower)
                                || contains(p.getInterfaceName(), lower)
                                || contains(p.getDescription(), lower))
                .collect(Collectors.toList());
    }

    private static boolean contains(@Nullable String value, String lowerKeyword) {
        return value != null && value.toLowerCase().contains(lowerKeyword);
    }

    @Override
    public ExtPoint save(ExtPoint extPoint) {
        ExtStudioExtensionPoint row = StudioPersistenceConverter.toEntity(extPoint);
        if (row.getId() == null) {
            repository.insert(row);
        } else if (repository.findById(row.getId()) != null) {
            repository.update(row);
        } else {
            repository.insert(row);
        }
        extPoint.setId(row.getId());
        return extPoint;
    }

    @Override
    public boolean update(ExtPoint extPoint) {
        if (extPoint == null || extPoint.getId() == null) {
            return false;
        }
        ExtStudioExtensionPoint row = StudioPersistenceConverter.toEntity(extPoint);
        row.setUpdatedAt(new Date());
        return repository.update(row);
    }

    @Override
    public boolean deleteById(Long id) {
        return id != null && repository.deleteById(id);
    }

    @Override
    public long count() {
        return repository.countByCriteria(Criteria.<ExtStudioExtensionPoint>create());
    }
}
