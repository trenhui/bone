package com.bone.metadata.catalog.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaRelationCommand;
import com.bone.metadata.catalog.domain.model.MetaEntityRelation;
import com.bone.metadata.catalog.domain.repository.MetaEntityRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateMetaRelationHandler {

  private final MetaEntityRelationRepository relationRepository;

  @Transactional
  public void handle(Long id, UpdateMetaRelationCommand cmd) {
    MetaEntityRelation relation = relationRepository.findById(id);
    if (relation == null) {
      throw BizException.of("关系不存在: " + id);
    }
    relation.update(
        cmd.getName(),
        cmd.getType(),
        cmd.getSourceFieldId(),
        cmd.getTargetFieldId(),
        cmd.getForeignKeyField(),
        cmd.getRequired(),
        cmd.getCascadeType());
    relationRepository.update(relation);
  }
}
