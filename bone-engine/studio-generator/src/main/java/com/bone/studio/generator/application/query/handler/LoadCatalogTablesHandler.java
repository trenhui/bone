package com.bone.studio.generator.application.query.handler;

import com.bone.core.capability.Capability;
import com.bone.core.model.PageResult;
import com.bone.studio.generator.application.query.qry.LoadCatalogTablesQuery;
import com.bone.studio.generator.domain.data.DatabaseTable;
import com.bone.studio.generator.domain.gateway.CatalogMetadataGateway;
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
public class LoadCatalogTablesHandler {

  private final CatalogMetadataGateway catalogMetadataGateway;

  @Transactional(readOnly = true)
  public PageResult<DatabaseTable> handle(LoadCatalogTablesQuery qry) {
    int page = Math.max(qry.getPage(), 1);
    int size = Math.min(Math.max(qry.getSize(), 1), 100);

    List<DatabaseTable> all =
        catalogMetadataGateway.loadPublishedSnapshots(qry.getTenantId(), qry.getEntityCodes());

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
