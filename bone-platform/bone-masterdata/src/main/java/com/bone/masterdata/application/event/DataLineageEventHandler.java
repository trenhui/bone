package com.bone.masterdata.application.event;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.domain.lineage.LineageRecord;
import com.bone.masterdata.domain.lineage.event.DataLineageEvent;
import com.bone.masterdata.domain.repository.LineageRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** 血缘事件监听：将 {@link DataLineageEvent} 落库为 {@link LineageRecord}。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataLineageEventHandler {

  private final LineageRecordRepository lineageRecordRepository;

  @TransactionalEventListener
  public void handle(DataLineageEvent event) {
    Long id = DistributedIdGenerator.generateLongId();
    LineageRecord record =
        LineageRecord.create(
            id,
            event.sourceEntity(),
            event.sourceField(),
            event.transformType(),
            event.targetEntity(),
            event.targetField(),
            event.schemaName());
    lineageRecordRepository.save(record);
    log.info(
        "血缘记录已保存: {}:{}=>{}:{} ({}: {})",
        event.sourceEntity(),
        event.sourceField(),
        event.targetEntity(),
        event.targetField(),
        event.transformType(),
        event.schemaName());
  }
}
