package com.bone.masterdata.application;

import com.bone.core.capability.Capability;
import com.bone.masterdata.application.command.cmd.RecordLineageCommand;
import com.bone.masterdata.application.event.MasterdataDomainEventPublisher;
import com.bone.masterdata.application.query.dto.LineageRecordDTO;
import com.bone.masterdata.application.query.qry.LineageQuery;
import com.bone.masterdata.domain.lineage.LineageRecord;
import com.bone.masterdata.domain.lineage.event.DataLineageEvent;
import com.bone.masterdata.domain.repository.LineageRecordRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据血缘应用服务（ADR-0028 Application Service First）。
 *
 * <p>原 {@code LineageRecordCommandHandler} 与 {@code LineageQueryHandler} 的全部用例已内联合并到本服务。
 * 血缘写入通过领域事件（{@link DataLineageEvent}）异步落库，读侧领域模型经由 {@link LineageRecordRepository} 的 default
 * 方法承载（ADR-0030）。
 */
@Service
@RequiredArgsConstructor
public class LineageApplicationService {

  private final LineageRecordRepository lineageRecordRepository;
  private final MasterdataDomainEventPublisher domainEventPublisher;

  @Capability(
      name = "RecordLineage",
      description = "记录数据血缘边并发布血缘事件",
      inputSchema =
          "{\"sourceEntity\": \"string\", \"sourceField\": \"string\", \"transformType\": \"string\", \"targetEntity\": \"string\", \"targetField\": \"string\", \"schemaName\": \"string\"}",
      outputSchema = "{\"recorded\": true}",
      idempotent = false,
      cost = 1,
      retryable = true,
      timeout = 5)
  @Transactional
  public void record(RecordLineageCommand cmd) {
    // 发布血缘事件，由 DataLineageEventHandler 在事务提交后落库 LineageRecord
    domainEventPublisher.publish(
        new DataLineageEvent(
            cmd.getSourceEntity(),
            cmd.getSourceField(),
            cmd.getTransformType(),
            cmd.getTargetEntity(),
            cmd.getTargetField(),
            cmd.getSchemaName()));
  }

  @Transactional(readOnly = true)
  public List<LineageRecordDTO> list(LineageQuery qry) {
    List<LineageRecord> records =
        lineageRecordRepository.findBySourceOrTarget(qry.getSourceEntity(), qry.getTargetEntity());
    return records.stream().map(this::toDto).toList();
  }

  private LineageRecordDTO toDto(LineageRecord r) {
    return LineageRecordDTO.builder()
        .id(r.getId())
        .sourceEntity(r.getSourceEntity())
        .sourceField(r.getSourceField())
        .transformType(r.getTransformType())
        .targetEntity(r.getTargetEntity())
        .targetField(r.getTargetField())
        .schemaName(r.getSchemaName())
        .createdAt(r.getCreatedAt())
        .build();
  }
}
