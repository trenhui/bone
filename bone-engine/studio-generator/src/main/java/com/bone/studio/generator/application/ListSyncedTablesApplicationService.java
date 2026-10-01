package com.bone.studio.generator.application;

import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.query.qry.ListSyncedTablesQuery;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.model.data.DataSource;
import com.bone.studio.generator.domain.model.data.DatabaseTable;
import com.bone.studio.generator.domain.model.data.GenColumnMetadata;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import com.bone.studio.generator.domain.model.data.TableColumn;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import com.bone.studio.generator.domain.repository.GenColumnMetadataRepository;
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
public class ListSyncedTablesApplicationService {

  private final GenTableMetadataRepository tableMetadataRepo;
  private final GenColumnMetadataRepository columnMetadataRepo;
  private final DataSourceRepository dataSourceRepository;

  /**
   * 列出已同步表，并带上列元数据。
   *
   * <p><b>为何必须带列</b>：此前只回填表名/注释，{@code columns} 恒为空数组——前端「列数」列永远显示 0，
   * 用户无法判断这张表到底同步成功没有（同步写库失败与同步成功在 UI 上长得一模一样）。
   */
  @Transactional(readOnly = true)
  public List<DatabaseTable> handle(ListSyncedTablesQuery qry) {
    Long dataSourcePk = StudioIds.parseRequired(qry.getDataSourceId());
    DataSource dataSource = dataSourceRepository.findById(dataSourcePk);
    if (dataSource == null) {
      return Collections.emptyList();
    }
    String dataSourceKey = StudioIds.dataSourceKey(dataSourcePk);
    List<GenTableMetadata> rows = tableMetadataRepo.findByDataSourceId(dataSourceKey);

    List<DatabaseTable> tables = new ArrayList<>();
    for (GenTableMetadata row : rows) {
      if (row.isDeleted()) {
        continue;
      }
      tables.add(
          DatabaseTable.builder()
              .tableName(row.getOriginalTableName())
              .tableComment(row.getTableComment() != null ? row.getTableComment() : "")
              .columns(toTableColumns(columnMetadataRepo.findByTableMetadataId(row.getId())))
              .build());
    }
    return tables;
  }

  /** 列元数据 → 读模型列；保持物理列顺序（{@code sort_order}）便于前端按表结构原序展示。 */
  private static List<TableColumn> toTableColumns(List<GenColumnMetadata> columns) {
    if (columns == null || columns.isEmpty()) {
      return List.of();
    }
    List<TableColumn> result = new ArrayList<>(columns.size());
    for (GenColumnMetadata column : columns) {
      if (column.isDeleted()) {
        continue;
      }
      Integer length = column.getColumnLength();
      Integer precision = column.getPrecisionValue();
      Integer scale = column.getScaleValue();
      result.add(
          TableColumn.builder()
              .columnName(column.getOriginalColumnName())
              .columnType(column.getColumnType())
              .columnComment(column.getColumnComment())
              .nullable(column.isNullable())
              .primaryKey(column.isPrimaryKey())
              .defaultValue(column.getDefaultValue())
              .length(length == null ? 0 : length)
              .precision(precision == null ? 0 : precision)
              .scale(scale == null ? 0 : scale)
              .build());
    }
    return List.copyOf(result);
  }
}
