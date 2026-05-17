package com.bone.metadata.catalog.application.query.handler;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.application.query.dto.MetaRelationDTO;
import com.bone.metadata.catalog.application.query.mapper.CatalogDtoMapper;
import com.bone.metadata.catalog.domain.model.MetaEntityRelation;
import com.bone.metadata.catalog.domain.repository.MetaEntityRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MetaRelationDetailQueryHandler {

  private final MetaEntityRelationRepository relationRepository;

  @Transactional(readOnly = true)
  public MetaRelationDTO handle(Long id) {
    MetaEntityRelation relation = relationRepository.findById(id);
    if (relation == null) {
      throw BizException.of("关系不存在: " + id);
    }
    return CatalogDtoMapper.toDto(relation);
  }
}
