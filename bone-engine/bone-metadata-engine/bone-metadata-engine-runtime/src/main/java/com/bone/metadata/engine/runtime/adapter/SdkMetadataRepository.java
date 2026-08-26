package com.bone.metadata.engine.runtime.adapter;

import com.bone.metadata.engine.domain.metadata.EntityMetadata;
import com.bone.metadata.engine.domain.metadata.OperationMetadata;
import com.bone.metadata.engine.domain.metadata.WorkflowMetadata;
import com.bone.metadata.engine.ports.spi.MetadataPlatformBridge;
import com.bone.metadata.engine.ports.spi.MetadataRepositoryPort;
import com.bone.metadata.engine.runtime.adapter.po.MetaEntityPo;
import com.bone.metadata.engine.runtime.adapter.po.MetaFieldPo;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 基于 {@code bone-metadata-sdk} 的 {@link MetadataRepositoryPort} 读侧实现（模式 B 接入）。
 *
 * <p>通过 SDK 的 {@code Repository<MetaEntityPo>} / {@code Repository<MetaFieldPo>} 读取已发布元数据， 经 {@link
 * MetaEntityConverter} 转换为引擎领域模型。属于适配器层（adapter），可依赖 SDK 与端口， 但领域层不感知 SDK。
 *
 * <p>多租户：所有查询统一经 {@link #withTenantEntity(Criteria)} / {@link #withTenantField(Criteria)} 注入 {@code
 * tenant_id} 过滤（取自 {@link MetadataPlatformBridge#currentTenantId()}），避免散落。
 */
@Component
public class SdkMetadataRepository implements MetadataRepositoryPort {

  private final Repository<MetaEntityPo, Long> entityRepository;
  private final Repository<MetaFieldPo, Long> fieldRepository;
  private final MetadataPlatformBridge platformBridge;

  public SdkMetadataRepository(
      Repository<MetaEntityPo, Long> entityRepository,
      Repository<MetaFieldPo, Long> fieldRepository,
      MetadataPlatformBridge platformBridge) {
    this.entityRepository = entityRepository;
    this.fieldRepository = fieldRepository;
    this.platformBridge = platformBridge;
  }

  /** 统一追加租户过滤条件（链式 eq，不破坏已有条件）。 */
  private Criteria<MetaEntityPo> withTenantEntity(Criteria<MetaEntityPo> criteria) {
    return platformBridge
        .currentTenantId()
        .map(tenantId -> criteria.eq("tenantId", tenantId))
        .orElse(criteria);
  }

  private Criteria<MetaFieldPo> withTenantField(Criteria<MetaFieldPo> criteria) {
    return platformBridge
        .currentTenantId()
        .map(tenantId -> criteria.eq("tenantId", tenantId))
        .orElse(criteria);
  }

  @Override
  public Optional<EntityMetadata> findEntityById(String id) {
    if (id == null || id.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(
            entityRepository.findOneByCriteria(
                withTenantEntity(Criteria.<MetaEntityPo>create().eq("id", Long.valueOf(id)))))
        .map(po -> MetaEntityConverter.toEntityMetadata(po, findFields(po.getId())));
  }

  @Override
  public EntityMetadata findEntityByApiName(String apiName) {
    MetaEntityPo po =
        entityRepository.findOneByCriteria(
            withTenantEntity(Criteria.<MetaEntityPo>create().eq("code", apiName)));
    if (po == null) {
      throw new IllegalStateException("元数据实体不存在: " + apiName);
    }
    return MetaEntityConverter.toEntityMetadata(po, findFields(po.getId()));
  }

  @Override
  public List<EntityMetadata> findAllEntities() {
    return toEntityMetadataList(
        entityRepository.findByCriteria(withTenantEntity(Criteria.<MetaEntityPo>create())));
  }

  @Override
  public List<EntityMetadata> findEntitiesByDomain(String domain) {
    // SDK 的 meta_entity 当前无 domain 维度列，本方法暂返回空列表。
    return List.of();
  }

  @Override
  public List<EntityMetadata> searchEntities(String query, int offset, int limit) {
    Criteria<MetaEntityPo> criteria = withTenantEntity(Criteria.<MetaEntityPo>create());
    if (query != null && !query.isEmpty()) {
      criteria = criteria.like("name", "%" + query + "%");
    }
    List<MetaEntityPo> pos = entityRepository.findByCriteria(criteria);
    int from = Math.max(0, offset);
    int to = Math.min(pos.size(), from + Math.max(0, limit));
    return toEntityMetadataList(pos.subList(from, to));
  }

  @Override
  public boolean existsEntity(String apiName) {
    return entityRepository.findOneByCriteria(
            withTenantEntity(Criteria.<MetaEntityPo>create().eq("code", apiName)))
        != null;
  }

  @Override
  public Set<String> findAllDomains() {
    // SDK 的 meta_entity 当前无 domain 维度列，本方法暂返回空集合。
    return Set.of();
  }

  @Override
  public List<EntityMetadata> findEntitiesByTag(String tag) {
    // SDK 的 meta_entity 当前无 tag 维度列，本方法暂返回空列表。
    return List.of();
  }

  @Override
  public List<OperationMetadata> findAllOperations() {
    // 操作元数据非 SDK meta_* 表范畴，留待 T2 领域模型归一时补充。
    return List.of();
  }

  @Override
  public Optional<WorkflowMetadata> findWorkflowByApiName(String apiName) {
    // 工作流元数据非 SDK meta_* 表范畴，留待 T2 领域模型归一时补充。
    return Optional.empty();
  }

  private List<MetaFieldPo> findFields(Long entityId) {
    return fieldRepository.findByCriteria(
        withTenantField(Criteria.<MetaFieldPo>create().eq("entityId", entityId)));
  }

  private List<EntityMetadata> toEntityMetadataList(List<MetaEntityPo> pos) {
    return pos.stream()
        .map(po -> MetaEntityConverter.toEntityMetadata(po, findFields(po.getId())))
        .toList();
  }
}
