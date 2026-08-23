package com.bone.masterdata.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.masterdata.application.command.cmd.RecordLineageCommand;
import com.bone.masterdata.application.event.MasterdataDomainEventPublisher;
import com.bone.masterdata.domain.lineage.event.DataLineageEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
@Component
@RequiredArgsConstructor
@Transactional
public class LineageRecordCommandHandler {
  private final MasterdataDomainEventPublisher domainEventPublisher;

  public void handle(RecordLineageCommand cmd) {
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
}
