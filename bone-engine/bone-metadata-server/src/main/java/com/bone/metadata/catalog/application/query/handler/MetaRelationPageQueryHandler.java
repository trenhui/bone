package com.bone.metadata.catalog.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.application.query.dto.MetaRelationDTO;
import com.bone.metadata.catalog.application.query.mapper.CatalogDtoMapper;
import com.bone.metadata.catalog.application.query.qry.MetaRelationPageQuery;
import com.bone.metadata.catalog.common.CatalogPageMapper;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.model.MetaEntityRelation;
import com.bone.metadata.catalog.domain.repository.MetaEntityRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class MetaRelationPageQueryHandler {

  private final MetaEntityRelationRepository relationRepository;
  private final TenantProvider tenantProvider;

  @Transactional(readOnly = true)
  public PageResult<MetaRelationDTO> handle(MetaRelationPageQuery qry) {
    long tenantId = tenantProvider.currentTenantId();
    var query = relationRepository.query().where(MetaEntityRelation::getTenantId).eq(tenantId);
    if (qry.getSourceEntityId() != null) {
      query = query.and(MetaEntityRelation::getSourceEntityId).eq(qry.getSourceEntityId());
    }
    if (qry.getTargetEntityId() != null) {
      query = query.and(MetaEntityRelation::getTargetEntityId).eq(qry.getTargetEntityId());
    }
    if (StringUtils.hasText(qry.getKeyword())) {
      query = query.and(MetaEntityRelation::getName).like("%" + qry.getKeyword().trim() + "%");
    }
    var sdkPage =
        query.orderByDesc(MetaEntityRelation::getId).page(qry.getPageNum(), qry.getPageSize());
    return CatalogPageMapper.toApiPage(sdkPage, CatalogDtoMapper::toDto);
  }
}
