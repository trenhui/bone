package com.bone.studio.generator.application;

import com.bone.core.model.PageResult;
import com.bone.studio.generator.application.query.qry.GetDataSourceListQuery;
import com.bone.studio.generator.common.GeneratorErrorCodes;
import com.bone.studio.generator.common.GeneratorErrors;
import com.bone.studio.generator.domain.gateway.TenantProvider;
import com.bone.studio.generator.domain.model.data.DataSource;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetDataSourceListQueryApplicationService {

  private final DataSourceRepository dataSourceRepository;
  private final TenantProvider tenantProvider;

  public PageResult<DataSource> handle(GetDataSourceListQuery qry) {
    Long tenantId = tenantProvider.currentTenantIdOrNull();
    if (tenantId == null) {
      throw GeneratorErrors.of(GeneratorErrorCodes.TENANT_CONTEXT_MISSING, null);
    }
    int page = qry.getPage() != null ? qry.getPage() : 1;
    int size = qry.getSize() != null ? qry.getSize() : 10;
    PageResult<DataSource> sdkPage = dataSourceRepository.findPageByTenant(tenantId, page, size);
    long total = sdkPage.getTotal() != null ? sdkPage.getTotal() : 0L;
    return PageResult.of(sdkPage.getRecords(), total, page, size);
  }
}
