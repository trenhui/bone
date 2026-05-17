package com.bone.metadata.catalog.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.domain.model.MetaField;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DeleteMetaFieldHandler {

  private final MetaFieldRepository metaFieldRepository;

  @Transactional
  public void handle(Long id) {
    MetaField field = metaFieldRepository.findById(id);
    if (field == null) {
      throw BizException.of("字段不存在: " + id);
    }
    metaFieldRepository.deleteById(id);
  }
}
