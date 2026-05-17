package com.bone.metadata.catalog.application.query.handler;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.application.query.dto.MetaFieldDTO;
import com.bone.metadata.catalog.application.query.mapper.CatalogDtoMapper;
import com.bone.metadata.catalog.domain.model.MetaField;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MetaFieldDetailQueryHandler {

  private final MetaFieldRepository metaFieldRepository;

  @Transactional(readOnly = true)
  public MetaFieldDTO handle(Long entityId, Long fieldId) {
    MetaField field = metaFieldRepository.findById(fieldId);
    if (field == null || !entityId.equals(field.getEntityId())) {
      throw BizException.of("字段不存在: " + fieldId);
    }
    return CatalogDtoMapper.toDto(field);
  }
}
