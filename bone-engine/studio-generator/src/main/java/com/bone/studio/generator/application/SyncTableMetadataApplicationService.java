package com.bone.studio.generator.application;

import com.bone.core.capability.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.studio.generator.application.command.cmd.SyncTableMetadataCommand;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.gateway.DatabaseMetadataGateway;
import com.bone.studio.generator.domain.gateway.GenTableMetadataReadPort;
import com.bone.studio.generator.domain.model.data.DataSource;
import com.bone.studio.generator.domain.model.data.DatabaseTable;
import com.bone.studio.generator.domain.model.data.GenColumnMetadata;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import com.bone.studio.generator.domain.model.data.TableColumn;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import com.bone.studio.generator.domain.repository.GenColumnMetadataRepository;
import com.bone.studio.generator.domain.repository.GenTableMetadataRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Component
@RequiredArgsConstructor
@Capability(
    name = "syncTableMetadata",
    description = "同步表元数据",
    inputSchema = "{}",
    outputSchema = "{}")
public class SyncTableMetadataApplicationService {

  private final DatabaseMetadataGateway metadataGateway;
  private final DataSourceRepository dataSourceRepository;
  private final GenTableMetadataRepository tableMetadataRepo;
  private final GenColumnMetadataRepository columnMetadataRepo;
  private final GenTableMetadataReadPort tableMetadataReadPort;

  @Transactional
  public void handle(SyncTableMetadataCommand cmd) {
    Long dataSourcePk = StudioIds.parseRequired(cmd.getDataSourceId());
    DataSource dataSource = dataSourceRepository.findById(dataSourcePk);
    if (dataSource == null) {
      throw new IllegalArgumentException("DataSource not found: " + cmd.getDataSourceId());
    }

    List<DatabaseTable> dbTables = metadataGateway.loadTables(dataSource);
    if (!CollectionUtils.isEmpty(cmd.getTableNames())) {
      Set<String> wanted =
          cmd.getTableNames().stream()
              .filter(n -> n != null && !n.isBlank())
              .collect(Collectors.toSet());
      dbTables = dbTables.stream().filter(t -> wanted.contains(t.getTableName())).toList();
    }
    String dataSourceKey = StudioIds.dataSourceKey(dataSourcePk);

    for (DatabaseTable dbTable : dbTables) {
      GenTableMetadata metadata = findExistingMetadata(dataSourceKey, dbTable.getTableName());
      if (metadata == null) {
        metadata =
            GenTableMetadata.create(
                DistributedIdGenerator.generateLongId(), 0L, dataSourceKey, dbTable);
        tableMetadataRepo.insert(metadata);
      } else {
        metadata.updateFrom(dbTable);
        tableMetadataRepo.update(metadata);
      }
      replaceColumns(metadata.getId(), dbTable);
    }
  }

  /**
   * 整表替换列元数据：列是表的从属数据，物理库改列后旧行会残留，先清后插保证与源库一致。
   *
   * <p>此前只写 {@code gen_table_metadata} 不写列，生成期拿到的列清单为空，实体只落 id 字段。
   */
  private void replaceColumns(Long tableMetadataId, DatabaseTable dbTable) {
    columnMetadataRepo.removeByTableMetadataId(tableMetadataId);

    List<TableColumn> dbColumns = dbTable.getColumns();
    if (CollectionUtils.isEmpty(dbColumns)) {
      return;
    }
    List<GenColumnMetadata> columns = new ArrayList<>(dbColumns.size());
    int sortOrder = 0;
    for (TableColumn dbColumn : dbColumns) {
      columns.add(
          GenColumnMetadata.builder()
              .id(DistributedIdGenerator.generateLongId())
              .tenantId(0L)
              .tableMetadataId(tableMetadataId)
              .originalColumnName(dbColumn.getColumnName())
              .customFieldName(dbColumn.getColumnName())
              .jdbcType(String.valueOf(dbColumn.getJdbcType()))
              .javaType(GenColumnMetadata.mapJdbcTypeToJavaType(dbColumn.getJdbcType()))
              .columnType(dbColumn.getColumnType())
              .columnLength(dbColumn.getColumnSize())
              .precisionValue(dbColumn.getColumnSize())
              .scaleValue(dbColumn.getDecimalDigits())
              .isNullable(dbColumn.isNullable())
              .isPrimaryKey(isPrimaryKey(dbTable, dbColumn.getColumnName()))
              .defaultValue(null)
              .columnComment(dbColumn.getColumnComment())
              .sortOrder(sortOrder++)
              .createdAt(LocalDateTime.now())
              .updatedAt(LocalDateTime.now())
              .deleted(false)
              .build());
    }
    columnMetadataRepo.batchInsert(columns);
  }

  private boolean isPrimaryKey(DatabaseTable dbTable, String columnName) {
    String primaryKey = dbTable.getPrimaryKey();
    if (primaryKey != null && !primaryKey.isBlank()) {
      return primaryKey.equals(columnName);
    }
    return "id".equalsIgnoreCase(columnName);
  }

  private GenTableMetadata findExistingMetadata(String dataSourceKey, String tableName) {
    return tableMetadataReadPort
        .findByDataSourceKeyAndTableName(dataSourceKey, tableName)
        .orElse(null);
  }
}
