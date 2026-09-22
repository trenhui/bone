package com.bone.engine.extension.studio.infrastructure.persistence;

import com.bone.engine.extension.studio.domain.gateway.ExtPointReadPort;
import com.bone.engine.extension.studio.domain.model.extpoint.ExtPoint;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import com.bone.engine.extension.studio.infrastructure.persistence.converter.StudioPersistenceConverter;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioExtensionPoint;
import com.bone.engine.extension.studio.infrastructure.persistence.repository.ExtStudioExtensionPointRepository;
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
public class MetadataExtPointRepository implements ExtPointRepository, ExtPointReadPort {

  private final ExtStudioExtensionPointRepository repository;

  public MetadataExtPointRepository(ExtStudioExtensionPointRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<ExtPoint> findAll() {
    return repository
        .findByCriteria(
            Criteria.<ExtStudioExtensionPoint>create()
                .orderByDesc(ExtStudioExtensionPoint::getCreatedAt))
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
    Criteria<ExtStudioExtensionPoint> criteria =
        Criteria.<ExtStudioExtensionPoint>create()
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
            p ->
                contains(p.getName(), lower)
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
  public boolean remove(Long id) {
    return id != null && repository.deleteById(id);
  }

  @Override
  public long count() {
    return repository.countByCriteria(Criteria.<ExtStudioExtensionPoint>create());
  }
}
