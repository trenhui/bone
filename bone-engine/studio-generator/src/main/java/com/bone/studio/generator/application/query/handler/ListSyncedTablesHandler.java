package com.bone.studio.generator.application.query.handler;

import com.bone.core.usecase.Capability;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.application.query.qry.ListSyncedTablesQry;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.data.DatabaseTable;
import com.bone.studio.generator.domain.data.GenTableMetadata;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import com.bone.studio.generator.domain.repository.GenTableMetadataRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Capability(
    name = "listSyncedTables",
    description = "列出已同步到 gen_table_metadata 的表",
    inputSchema = "{}",
    outputSchema = "{}")
public class ListSyncedTablesHandler {

  private final GenTableMetadataRepository tableMetadataRepo;
  private final DataSourceRepository dataSourceRepository;

  @Transactional(readOnly = true)
  public List<DatabaseTable> handle(ListSyncedTablesQry qry) {
    Long dataSourcePk = StudioIds.parseRequired(qry.getDataSourceId());
    DataSource dataSource = dataSourceRepository.findById(dataSourcePk);
    if (dataSource == null) {
      return Collections.emptyList();
    }
    String dataSourceKey = StudioIds.dataSourceKey(dataSourcePk);
    List<GenTableMetadata> rows =
        tableMetadataRepo.findByCriteria(
            Criteria.<GenTableMetadata>create().eq("dataSourceId", dataSourceKey));

    List<DatabaseTable> tables = new ArrayList<>();
    for (GenTableMetadata row : rows) {
      if (row.isDeleted()) {
        continue;
      }
      tables.add(
          DatabaseTable.builder()
              .tableName(row.getOriginalTableName())
              .tableComment(row.getTableComment() != null ? row.getTableComment() : "")
              .build());
    }
    return tables;
  }
}
