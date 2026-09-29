package com.bone.studio.generator.application;

import com.bone.core.capability.Capability;
import com.bone.core.model.PageResult;
import com.bone.studio.generator.application.query.qry.LoadCatalogTablesQuery;
import com.bone.studio.generator.common.GeneratorErrorCodes;
import com.bone.studio.generator.common.GeneratorErrors;
import com.bone.studio.generator.domain.gateway.CatalogMetadataGateway;
import com.bone.studio.generator.domain.gateway.TenantProvider;
import com.bone.studio.generator.domain.model.data.DatabaseTable;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Capability(
    name = "loadCatalogTables",
    description = "分页加载已发布元数据实体快照",
    inputSchema = "{}",
    outputSchema = "{}")
public class LoadCatalogTablesApplicationService {

  private final CatalogMetadataGateway catalogMetadataGateway;
  private final TenantProvider tenantProvider;

  @Transactional(readOnly = true)
  public PageResult<DatabaseTable> handle(LoadCatalogTablesQuery qry) {
    int page = Math.max(qry.getPage(), 1);
    int size = Math.min(Math.max(qry.getSize(), 1), 100);

    // 查询未显式指定租户时回落到可信上下文，而不是网关里的魔法默认 1L。
    Long tenantId =
        qry.getTenantId() != null ? qry.getTenantId() : tenantProvider.currentTenantIdOrNull();
    if (tenantId == null) {
      throw GeneratorErrors.of(GeneratorErrorCodes.TENANT_CONTEXT_MISSING, qry.getEntityCodes());
    }

    List<DatabaseTable> all =
        catalogMetadataGateway.loadPublishedSnapshots(tenantId, qry.getEntityCodes());

    if (StringUtils.hasText(qry.getKeyword())) {
      String kw = qry.getKeyword().trim().toLowerCase();
      all =
          all.stream()
              .filter(
                  t ->
                      (t.getTableName() != null && t.getTableName().toLowerCase().contains(kw))
                          || (t.getTableComment() != null
                              && t.getTableComment().toLowerCase().contains(kw)))
              .toList();
    }

    long total = all.size();
    int from = (page - 1) * size;
    int to = Math.min(from + size, all.size());
    List<DatabaseTable> slice = from >= all.size() ? List.of() : all.subList(from, to);

    return PageResult.of(slice, total, page, size);
  }
}
