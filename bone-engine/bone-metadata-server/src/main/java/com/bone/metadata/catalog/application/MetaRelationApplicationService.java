package com.bone.metadata.catalog.application;

import com.bone.core.exception.BizException;
import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaRelationCommand;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaRelationCommand;
import com.bone.metadata.catalog.application.query.dto.MetaRelationDTO;
import com.bone.metadata.catalog.application.query.mapper.CatalogDtoMapper;
import com.bone.metadata.catalog.common.CatalogPageMapper;
import com.bone.metadata.catalog.common.CatalogVersionSupport;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaEntityRelation;
import com.bone.metadata.catalog.domain.repository.MetaEntityRelationRepository;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 元数据关系建模应用层统一门面（ADR-0028 Application Service First）。
 *
 * <p>实体 / 字段建模见 {@link MetaEntityApplicationService}；本门面仅承载「实体间关系」限界上下文。 读侧经 domain.repository
 * 读模型方法（ADR-0030），不另建 QueryPort。
 */
@Service
@RequiredArgsConstructor
public class MetaRelationApplicationService {

  private final MetaEntityRelationRepository relationRepository;
  private final MetaEntityRepository metaEntityRepository;
  private final TenantProvider tenantProvider;

  @Transactional
  public Long createRelation(CreateMetaRelationCommand cmd) {
    if (cmd.getType() == null && cmd.getRelationType() != null) {
      cmd.setType(cmd.getRelationType());
    }
    requireEntity(cmd.getSourceEntityId());
    requireEntity(cmd.getTargetEntityId());
    MetaEntityRelation relation =
        MetaEntityRelation.create(
            null,
            tenantProvider.currentTenantId(),
            cmd.getName(),
            cmd.getSourceEntityId(),
            cmd.getTargetEntityId(),
            cmd.getType());
    relation.update(
        cmd.getName(),
        cmd.getType(),
        cmd.getSourceFieldId(),
        cmd.getTargetFieldId(),
        cmd.getForeignKeyField(),
        cmd.getRequired(),
        cmd.getCascadeType());
    relationRepository.insert(relation);
    return relation.getId();
  }

  @Transactional
  public Integer updateRelation(Long id, UpdateMetaRelationCommand cmd, Integer expectedVersion) {
    MetaEntityRelation relation = relationRepository.findById(id);
    if (relation == null) {
      throw BizException.of("关系不存在: " + id);
    }
    CatalogVersionSupport.assertExpected(expectedVersion, relation.getVersion());
    relation.update(
        cmd.getName(),
        cmd.getType(),
        cmd.getSourceFieldId(),
        cmd.getTargetFieldId(),
        cmd.getForeignKeyField(),
        cmd.getRequired(),
        cmd.getCascadeType());
    relationRepository.update(relation);
    return relation.getVersion();
  }

  @Transactional
  public void deleteRelation(Long id) {
    MetaEntityRelation relation = relationRepository.findById(id);
    if (relation == null) {
      throw BizException.of("关系不存在: " + id);
    }
    relationRepository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public MetaRelationDTO getRelation(Long id) {
    MetaEntityRelation relation = relationRepository.findById(id);
    if (relation == null) {
      throw BizException.of("关系不存在: " + id);
    }
    return CatalogDtoMapper.toDto(relation);
  }

  @Transactional(readOnly = true)
  public PageResult<MetaRelationDTO> pageRelations(
      Long sourceEntityId, Long targetEntityId, String keyword, int pageNum, int pageSize) {
    long tenantId = tenantProvider.currentTenantId();
    PageResult<MetaEntityRelation> sdkPage =
        relationRepository.pageRelations(
            tenantId, sourceEntityId, targetEntityId, keyword, pageNum, pageSize);
    return CatalogPageMapper.toApiPage(sdkPage, CatalogDtoMapper::toDto);
  }

  private void requireEntity(Long entityId) {
    MetaEntity entity = metaEntityRepository.findById(entityId);
    if (entity == null) {
      throw BizException.of("实体不存在: " + entityId);
    }
  }
}
