package com.bone.metadata.catalog.application.query.handler;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.application.query.dto.MetaEntityDTO;
import com.bone.metadata.catalog.application.query.mapper.CatalogDtoMapper;
import com.bone.metadata.catalog.domain.model.MetaEntity;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MetaEntityDetailQueryHandler {

  private final MetaEntityRepository metaEntityRepository;

  @Transactional(readOnly = true)
  public MetaEntityDTO handle(Long id) {
    MetaEntity entity = metaEntityRepository.findById(id);
    if (entity == null) {
      throw BizException.of("实体不存在: " + id);
    }
    return CatalogDtoMapper.toDto(entity);
  }
}
