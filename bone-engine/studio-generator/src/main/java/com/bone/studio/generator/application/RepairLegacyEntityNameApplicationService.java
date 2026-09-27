package com.bone.studio.generator.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.command.cmd.RepairLegacyEntityNameCommand;
import com.bone.studio.generator.application.dto.RepairEntityNameResult;
import com.bone.studio.generator.application.dto.RepairEntityNameResult.RepairEntityNameExample;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import com.bone.studio.generator.domain.repository.GenTableMetadataRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 一次性运维修复：将存量 {@code custom_entity_name} 未转驼峰的行收敛为 PascalCase。
 *
 * <p>仅修正 {@code customEntityName == originalTableName} 的行（即从未被用户自定义过）；用户已改名的行保持不动。 识别与修复的领域规则下沉在
 * {@link GenTableMetadata#needsLegacyEntityNameRepair()} / {@link
 * GenTableMetadata#repairEntityName()}。逻辑与 {@link GenTableMetadata#toEntityName(String)} 一致。
 *
 * <p>execute=false 时只统计受影响行并返回示例，不写库（幂等、可重复预览）。
 */
@Component
@RequiredArgsConstructor
@NoDomainEvent
@Capability(
    name = "repairLegacyEntityName",
    description = "一次性运维修复：将存量未转驼峰的 custom_entity_name 收敛为 PascalCase",
    inputSchema = "{\"execute\":\"boolean 默认false,仅预览\",\"dataSourceId\":\"string 可选,限定数据源\"}",
    outputSchema = "{\"executed\":\"boolean\",\"affectedCount\":\"int\",\"examples\":\"array\"}")
@Slf4j
public class RepairLegacyEntityNameApplicationService {

  private final GenTableMetadataRepository tableMetadataRepo;

  @Transactional
  public RepairEntityNameResult handle(RepairLegacyEntityNameCommand cmd) {
    List<GenTableMetadata> rows = tableMetadataRepo.findAllActive();

    List<RepairEntityNameExample> examples = new ArrayList<>();
    int affected = 0;

    for (GenTableMetadata row : rows) {
      if (cmd.getDataSourceId() != null && !cmd.getDataSourceId().equals(row.getDataSourceId())) {
        continue;
      }
      if (!row.needsLegacyEntityNameRepair()) {
        continue;
      }
      examples.add(
          new RepairEntityNameExample(
              row.getId(),
              row.getDataSourceId(),
              row.getOriginalTableName(),
              row.getCustomEntityName(),
              GenTableMetadata.toEntityName(row.getOriginalTableName())));
      if (cmd.isExecute()) {
        row.repairEntityName();
        tableMetadataRepo.update(row);
      }
      affected++;
    }

    if (cmd.isExecute()) {
      log.info("[repairLegacyEntityName] 已修复 {} 行 custom_entity_name", affected);
    } else {
      log.info("[repairLegacyEntityName] 预览：将修复 {} 行 custom_entity_name（未执行）", affected);
    }
    return new RepairEntityNameResult(cmd.isExecute(), affected, examples);
  }
}
