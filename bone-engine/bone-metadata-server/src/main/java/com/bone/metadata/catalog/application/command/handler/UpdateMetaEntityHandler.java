package com.bone.metadata.catalog.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaEntityCmd;
import com.bone.metadata.catalog.domain.model.MetaEntity;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateMetaEntityHandler {

  private final MetaEntityRepository metaEntityRepository;

  @Transactional
  public void handle(Long id, UpdateMetaEntityCmd cmd) {
    MetaEntity entity = metaEntityRepository.findById(id);
    if (entity == null) {
      throw BizException.of("实体不存在: " + id);
    }
    entity.update(
        cmd.getName(),
        cmd.getDisplayName(),
        cmd.getDescription(),
        cmd.getTableName(),
        cmd.getSortOrder(),
        cmd.getIcon());
    metaEntityRepository.update(entity);
  }
}
