package com.bone.metadata.catalog.application.query.handler;

import com.bone.core.result.PageResult;
import com.bone.metadata.catalog.application.query.dto.MetaFieldDTO;
import com.bone.metadata.catalog.application.query.mapper.CatalogDtoMapper;
import com.bone.metadata.catalog.application.query.qry.MetaFieldPageQry;
import com.bone.metadata.catalog.common.CatalogPageMapper;
import com.bone.metadata.catalog.domain.model.MetaField;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class MetaFieldPageQueryHandler {

  private final MetaFieldRepository metaFieldRepository;

  @Transactional(readOnly = true)
  public PageResult<MetaFieldDTO> handle(MetaFieldPageQry qry) {
    var query = metaFieldRepository.query();
    if (qry.getEntityId() != null) {
      query = query.where(MetaField::getEntityId).eq(qry.getEntityId());
    }
    if (StringUtils.hasText(qry.getKeyword())) {
      if (qry.getEntityId() != null) {
        query = query.and(MetaField::getName).like("%" + qry.getKeyword().trim() + "%");
      } else {
        query = query.where(MetaField::getName).like("%" + qry.getKeyword().trim() + "%");
      }
    }
    var sdkPage = query.orderByAsc(MetaField::getSortOrder).page(qry.getPageNum(), qry.getPageSize());
    return CatalogPageMapper.toApiPage(sdkPage, CatalogDtoMapper::toDto);
  }
}
