package com.bone.masterdata.application.event;

import com.bone.masterdata.domain.model.record.event.MasterDataRecordCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class MasterDataRecordCreatedHandler {

    @TransactionalEventListener
    public void handle(MasterDataRecordCreatedEvent event) {
        log.info("主数据记录创建事件: entityId={}, recordId={}",
                event.entityId(), event.recordId());
        // 这里可以添加业务逻辑
    }
}
