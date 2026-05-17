package com.bone.metadata.catalog.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaFieldCmd;
import com.bone.metadata.catalog.domain.model.MetaField;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateMetaFieldHandler {

  private final MetaFieldRepository metaFieldRepository;

  @Transactional
  public void handle(Long id, UpdateMetaFieldCmd cmd) {
    MetaField field = metaFieldRepository.findById(id);
    if (field == null) {
      throw BizException.of("字段不存在: " + id);
    }
    field.update(
        cmd.getDisplayName(),
        cmd.getType(),
        cmd.getLength(),
        cmd.getRequired(),
        cmd.getUnique(),
        cmd.getDefaultValue(),
        cmd.getComment(),
        cmd.getSortOrder());
    metaFieldRepository.update(field);
  }
}
