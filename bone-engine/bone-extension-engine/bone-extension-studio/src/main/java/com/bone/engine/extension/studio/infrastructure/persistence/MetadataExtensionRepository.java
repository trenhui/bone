package com.bone.engine.extension.studio.infrastructure.persistence;

import com.bone.engine.extension.studio.domain.gateway.ExtensionReadPort;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.repository.ExtensionRepository;
import com.bone.engine.extension.studio.infrastructure.persistence.converter.StudioPersistenceConverter;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioExtensionImpl;
import com.bone.engine.extension.studio.infrastructure.persistence.repository.ExtStudioExtensionImplRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@ConditionalOnProperty(
    prefix = "bone.extension.studio.persistence",
    name = "mode",
    havingValue = "metadata")
public class MetadataExtensionRepository implements ExtensionRepository, ExtensionReadPort {

  private final ExtStudioExtensionImplRepository repository;

  public MetadataExtensionRepository(ExtStudioExtensionImplRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<Extension> findAll() {
    return repository
        .findByCriteria(
            Criteria.<ExtStudioExtensionImpl>create()
                .orderByDesc(ExtStudioExtensionImpl::getCreatedAt))
        .stream()
        .map(StudioPersistenceConverter::toDomain)
        .sorted(Comparator.comparing(Extension::getId, Comparator.nullsLast(Long::compareTo)))
        .collect(Collectors.toList());
  }

  @Override
  @Nullable
  public Extension findById(Long id) {
    ExtStudioExtensionImpl row = repository.findById(id);
    return row == null ? null : StudioPersistenceConverter.toDomain(row);
  }

  @Override
  @Nullable
  public Extension findByClassName(String className) {
    if (!StringUtils.hasText(className)) {
      return null;
    }
    Criteria<ExtStudioExtensionImpl> criteria =
        Criteria.<ExtStudioExtensionImpl>create()
            .eq(ExtStudioExtensionImpl::getClassName, className.trim());
    List<ExtStudioExtensionImpl> rows = repository.findByCriteria(criteria);
    return rows.isEmpty() ? null : StudioPersistenceConverter.toDomain(rows.get(0));
  }

  @Override
  public List<Extension> findByExtPointId(Long extPointId) {
    if (extPointId == null) {
      return List.of();
    }
    Criteria<ExtStudioExtensionImpl> criteria =
        Criteria.<ExtStudioExtensionImpl>create()
            .eq(ExtStudioExtensionImpl::getExtensionPointId, extPointId)
            .orderByAsc(ExtStudioExtensionImpl::getPriority);
    return repository.findByCriteria(criteria).stream()
        .map(StudioPersistenceConverter::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public List<Extension> findByTenantCode(String tenantCode) {
    if (!StringUtils.hasText(tenantCode)) {
      return List.of();
    }
    Criteria<ExtStudioExtensionImpl> criteria =
        Criteria.<ExtStudioExtensionImpl>create()
            .eq(ExtStudioExtensionImpl::getTenantCode, tenantCode.trim());
    return repository.findByCriteria(criteria).stream()
        .map(StudioPersistenceConverter::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public List<Extension> findByAppId(Long appId) {
    if (appId == null) {
      return List.of();
    }
    Criteria<ExtStudioExtensionImpl> criteria =
        Criteria.<ExtStudioExtensionImpl>create()
            .eq(ExtStudioExtensionImpl::getAppId, appId)
            .orderByAsc(ExtStudioExtensionImpl::getPriority);
    return repository.findByCriteria(criteria).stream()
        .map(StudioPersistenceConverter::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public List<Extension> search(String keyword) {
    if (!StringUtils.hasText(keyword)) {
      return findAll();
    }
    String lower = keyword.trim().toLowerCase();
    return findAll().stream()
        .filter(
            e ->
                contains(e.getName(), lower)
                    || contains(e.getClassName(), lower)
                    || contains(e.getDescription(), lower))
        .collect(Collectors.toList());
  }

  @Override
  public Extension save(Extension extension) {
    ExtStudioExtensionImpl row = StudioPersistenceConverter.toEntity(extension);
    if (row.getId() == null) {
      repository.insert(row);
    } else if (repository.findById(row.getId()) != null) {
      repository.update(row);
    } else {
      repository.insert(row);
    }
    extension.setId(row.getId());
    return extension;
  }

  @Override
  public boolean remove(Long id) {
    return id != null && repository.deleteById(id);
  }

  @Override
  public long count() {
    return repository.countByCriteria(Criteria.<ExtStudioExtensionImpl>create());
  }

  private static boolean contains(@Nullable String value, String lowerKeyword) {
    return value != null && value.toLowerCase().contains(lowerKeyword);
  }
}
