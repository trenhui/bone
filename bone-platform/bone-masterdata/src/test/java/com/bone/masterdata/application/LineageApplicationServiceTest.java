package com.bone.masterdata.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.bone.masterdata.application.command.RecordLineageCommand;
import com.bone.masterdata.application.event.MasterdataDomainEventPublisher;
import com.bone.masterdata.domain.model.lineage.event.DataLineageEvent;
import com.bone.masterdata.domain.repository.LineageRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LineageApplicationServiceTest {

  @Mock private LineageRecordRepository lineageRecordRepository;
  @Mock private MasterdataDomainEventPublisher domainEventPublisher;

  @InjectMocks private LineageApplicationService service;

  /** 血缘靠事件在事务提交后落库：发布缺失 = 数据静默丢失。 */
  @Test
  void recordPublishesLineageEvent() {
    RecordLineageCommand cmd = new RecordLineageCommand();
    cmd.setSourceEntity("order");
    cmd.setTargetEntity("md_customer");
    cmd.setTransformType("SYNC");

    service.record(cmd);
    verify(domainEventPublisher).publish(any(DataLineageEvent.class));
  }
}
