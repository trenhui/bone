package com.bone.masterdata.application.query.handler;

import com.bone.masterdata.application.query.dto.LineageRecordDTO;
import com.bone.masterdata.application.query.qry.LineageQuery;
import com.bone.masterdata.domain.lineage.LineageRecord;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LineageQueryHandler {

  /** 查询血缘边列表（可按 target 查上游，或按 source 查下游）。 */
  public List<LineageRecordDTO> list(LineageQuery qry) {
    FluentQuery<LineageRecord> query = QueryBuilder.from(LineageRecord.class);
    if (qry.getTargetEntity() != null && !qry.getTargetEntity().isBlank()) {
      query.where(LineageRecord::getTargetEntity).eq(qry.getTargetEntity());
    }
    if (qry.getSourceEntity() != null && !qry.getSourceEntity().isBlank()) {
      query.where(LineageRecord::getSourceEntity).eq(qry.getSourceEntity());
    }
    return query.list().stream().map(this::toDTO).collect(Collectors.toList());
  }

  private LineageRecordDTO toDTO(LineageRecord record) {
    return LineageRecordDTO.builder()
        .id(record.getId())
        .sourceEntity(record.getSourceEntity())
        .sourceField(record.getSourceField())
        .transformType(record.getTransformType())
        .targetEntity(record.getTargetEntity())
        .targetField(record.getTargetField())
        .schemaName(record.getSchemaName())
        .createdAt(record.getCreatedAt())
        .build();
  }
}
