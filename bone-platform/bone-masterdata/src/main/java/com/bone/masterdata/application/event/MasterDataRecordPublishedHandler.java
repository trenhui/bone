package com.bone.masterdata.application.event;

import com.bone.masterdata.domain.model.record.event.MasterDataRecordPublishedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class MasterDataRecordPublishedHandler {

    @TransactionalEventListener
    public void handle(MasterDataRecordPublishedEvent event) {
        log.info("主数据记录发布事件: recordId={}, entityId={}", event.recordId(), event.entityId());
        // 这里可以添加业务逻辑，比如同步到其他系统等
    }
}
