package com.bone.metadata.catalog.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaRelationCommand;
import com.bone.metadata.catalog.common.CatalogTenantSupport;
import com.bone.metadata.catalog.domain.model.MetaEntity;
import com.bone.metadata.catalog.domain.model.MetaEntityRelation;
import com.bone.metadata.catalog.domain.repository.MetaEntityRelationRepository;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateMetaRelationHandler {

  private final MetaEntityRelationRepository relationRepository;
  private final MetaEntityRepository metaEntityRepository;

  @Transactional
  public Long handle(CreateMetaRelationCommand cmd) {
    requireEntity(cmd.getSourceEntityId());
    requireEntity(cmd.getTargetEntityId());
    Long id = DistributedIdGenerator.generateLongId();
    MetaEntityRelation relation =
        MetaEntityRelation.create(
            id,
            CatalogTenantSupport.currentTenantId(),
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
    return id;
  }

  private void requireEntity(Long entityId) {
    MetaEntity entity = metaEntityRepository.findById(entityId);
    if (entity == null) {
      throw BizException.of("实体不存在: " + entityId);
    }
  }
}
