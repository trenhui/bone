package com.bone.metadata.engine.runtime.adapter;

import com.bone.metadata.engine.domain.metadata.EntityMetadata;
import com.bone.metadata.engine.domain.metadata.OperationMetadata;
import com.bone.metadata.engine.domain.metadata.WorkflowMetadata;
import com.bone.metadata.engine.ports.spi.MetadataRepositoryPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link MetadataRepositoryPort} 的内存实现（runtime 层测试/回退实现）。
 *
 * <p>不依赖 SDK / 数据源，用于单元测试或宿主未接真实存储时的内存回退。写侧不在本端口范围，本类仅供读。
 */
public class InMemoryMetadataRepositoryPort implements MetadataRepositoryPort {

  private final Map<String, EntityMetadata> entityByApiName = new ConcurrentHashMap<>();

  /** 注册一个实体元数据（按 apiName 索引）。 */
  public InMemoryMetadataRepositoryPort register(EntityMetadata entity) {
    if (entity != null && entity.getApiName() != null) {
      entityByApiName.put(entity.getApiName(), entity);
    }
    return this;
  }

  @Override
  public Optional<EntityMetadata> findEntityById(String id) {
    return entityByApiName.values().stream()
        .filter(e -> id != null && id.equals(e.getId()))
        .findFirst();
  }

  @Override
  public EntityMetadata findEntityByApiName(String apiName) {
    return entityByApiName.get(apiName);
  }

  @Override
  public List<EntityMetadata> findAllEntities() {
    return new ArrayList<>(entityByApiName.values());
  }

  @Override
  public List<EntityMetadata> findEntitiesByDomain(String domain) {
    return entityByApiName.values().stream()
        .filter(e -> domain != null && domain.equals(e.getDomain()))
        .toList();
  }

  @Override
  public List<EntityMetadata> searchEntities(String query, int offset, int limit) {
    List<EntityMetadata> matched =
        entityByApiName.values().stream()
            .filter(e -> query == null || query.isBlank() || containsIgnoreCase(e.getName(), query))
            .toList();
    int from = Math.max(0, offset);
    int to = Math.min(matched.size(), from + Math.max(0, limit));
    return matched.subList(from, to);
  }

  @Override
  public boolean existsEntity(String apiName) {
    return entityByApiName.containsKey(apiName);
  }

  @Override
  public Set<String> findAllDomains() {
    return entityByApiName.values().stream()
        .map(EntityMetadata::getDomain)
        .collect(java.util.stream.Collectors.toSet());
  }

  @Override
  public List<EntityMetadata> findEntitiesByTag(String tag) {
    return entityByApiName.values().stream()
        .filter(e -> e.getTags() != null && e.getTags().contains(tag))
        .toList();
  }

  @Override
  public List<OperationMetadata> findAllOperations() {
    return List.of();
  }

  @Override
  public Optional<WorkflowMetadata> findWorkflowByApiName(String apiName) {
    return Optional.empty();
  }

  private static boolean containsIgnoreCase(String value, String keyword) {
    return value != null && keyword != null && value.toLowerCase().contains(keyword.toLowerCase());
  }
}
