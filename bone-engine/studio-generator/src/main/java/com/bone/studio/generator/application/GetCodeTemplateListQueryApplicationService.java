package com.bone.studio.generator.application;

import com.bone.core.model.PageResult;
import com.bone.studio.generator.application.query.qry.GetCodeTemplateListQuery;
import com.bone.studio.generator.common.GeneratorErrorCodes;
import com.bone.studio.generator.common.GeneratorErrors;
import com.bone.studio.generator.domain.gateway.TenantProvider;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetCodeTemplateListQueryApplicationService {

  private final CodeTemplateRepository codeTemplateRepository;
  private final TenantProvider tenantProvider;

  public PageResult<CodeTemplate> handle(GetCodeTemplateListQuery qry) {
    Long tenantId = tenantProvider.currentTenantIdOrNull();
    if (tenantId == null) {
      throw GeneratorErrors.of(GeneratorErrorCodes.TENANT_CONTEXT_MISSING, null);
    }
    int pageNo = qry.getPage() != null ? qry.getPage() : 1;
    int pageSize = qry.getSize() != null ? qry.getSize() : 10;

    PageResult<CodeTemplate> ownPage =
        codeTemplateRepository.findPageByTenant(tenantId, pageNo, pageSize);
    // 平台级内置模板种子对所有租户可见：受控跨租户只读（仅 tenant=0 且无创建人），由本服务（已登记的合法调用方）合并。
    if (tenantId != CodeTemplateRepository.PLATFORM_TENANT_ID) {
      PageResult<CodeTemplate> platformPage =
          codeTemplateRepository.findPlatformTemplatesAllTenants(1, 1000);
      Map<Long, CodeTemplate> merged = new LinkedHashMap<>();
      for (CodeTemplate t : ownPage.getRecords()) {
        merged.put(t.getId(), t);
      }
      for (CodeTemplate t : platformPage.getRecords()) {
        merged.putIfAbsent(t.getId(), t);
      }
      return PageResult.of(
          new ArrayList<>(merged.values()), (long) merged.size(), pageNo, pageSize);
    }
    long total = ownPage.getTotal() != null ? ownPage.getTotal() : 0L;
    return PageResult.of(ownPage.getRecords(), total, pageNo, pageSize);
  }
}
