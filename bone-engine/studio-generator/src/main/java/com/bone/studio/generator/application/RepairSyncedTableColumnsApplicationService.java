package com.bone.studio.generator.application;

import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.command.cmd.SyncTableMetadataCommand;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import com.bone.studio.generator.domain.repository.GenColumnMetadataRepository;
import com.bone.studio.generator.domain.repository.GenTableMetadataRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 存量同步表列元数据回填（运维修复）。
 *
 * <p><b>为何需要</b>：早期版本的同步只写 {@code gen_table_metadata} 不写列（列恒为空），存量表在 「已同步表列表」里列数永远显示 0，且基于它们生成的实体只有
 * id 字段。列数据只能从物理库现取， 无法用 SQL 回填；而 {@link SyncTableMetadataApplicationService#handle} 本身幂等 （{@code
 * updateFrom + 整表替换列}），对缺列表重新同步即是修复。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Capability(
    name = "repairSyncedTableColumns",
    description = "回填存量同步表中缺失的列元数据",
    inputSchema = "{}",
    outputSchema = "{}")
public class RepairSyncedTableColumnsApplicationService {

  private final GenTableMetadataRepository tableMetadataRepo;
  private final GenColumnMetadataRepository columnMetadataRepo;
  private final SyncTableMetadataApplicationService syncTableMetadataApplicationService;

  /**
   * @return {@code {repairedCount, tables}}：实际回填的表数与表名列表（无缺列时为空列表）
   */
  public Map<String, Object> handle(String dataSourceId) {
    Long dataSourcePk = StudioIds.parseRequired(dataSourceId);
    String dataSourceKey = StudioIds.dataSourceKey(dataSourcePk);
    List<GenTableMetadata> rows = tableMetadataRepo.findByDataSourceId(dataSourceKey);

    List<String> zeroColumnTables = new ArrayList<>();
    for (GenTableMetadata row : rows) {
      if (row.isDeleted()) {
        continue;
      }
      var existingColumns = columnMetadataRepo.findByTableMetadataId(row.getId());
      int columnCount =
          existingColumns == null
              ? 0
              : (int) existingColumns.stream().filter(c -> !c.isDeleted()).count();
      if (columnCount == 0) {
        zeroColumnTables.add(row.getOriginalTableName());
      }
    }
    if (zeroColumnTables.isEmpty()) {
      return Map.of("repairedCount", 0, "tables", List.of());
    }

    SyncTableMetadataCommand cmd =
        SyncTableMetadataCommand.builder()
            .dataSourceId(dataSourceId)
            .tableNames(zeroColumnTables)
            .build();
    int synced = syncTableMetadataApplicationService.handle(cmd);
    log.info("[存量列回填] 数据源 {} 回填 {} 张表的列元数据: {}", dataSourceId, synced, zeroColumnTables);
    return Map.of("repairedCount", synced, "tables", zeroColumnTables);
  }
}
