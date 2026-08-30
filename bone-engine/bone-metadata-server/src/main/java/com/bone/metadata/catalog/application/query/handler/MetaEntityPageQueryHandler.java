package com.bone.metadata.catalog.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.application.query.dto.MetaEntityDTO;
import com.bone.metadata.catalog.application.query.mapper.CatalogDtoMapper;
import com.bone.metadata.catalog.application.query.qry.MetaEntityPageQuery;
import com.bone.metadata.catalog.common.CatalogPageMapper;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.model.MetaEntity;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class MetaEntityPageQueryHandler {

  private final MetaEntityRepository metaEntityRepository;
  private final TenantProvider tenantProvider;

  @Transactional(readOnly = true)
  public PageResult<MetaEntityDTO> handle(MetaEntityPageQuery qry) {
    long tenantId = tenantProvider.currentTenantId();
    var query = metaEntityRepository.query().where(MetaEntity::getTenantId).eq(tenantId);
    if (StringUtils.hasText(qry.getKeyword())) {
      query = query.and(MetaEntity::getName).like("%" + qry.getKeyword().trim() + "%");
    }
    if (qry.getStatus() != null) {
      query = query.and(MetaEntity::getStatus).eq(qry.getStatus());
    }
    var sdkPage = query.orderByDesc(MetaEntity::getId).page(qry.getPageNum(), qry.getPageSize());
    return CatalogPageMapper.toApiPage(sdkPage, CatalogDtoMapper::toDto);
  }
}
