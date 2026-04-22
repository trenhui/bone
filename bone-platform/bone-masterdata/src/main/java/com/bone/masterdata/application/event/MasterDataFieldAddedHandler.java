package com.bone.masterdata.application.event;

import com.bone.masterdata.domain.model.field.event.MasterDataFieldAddedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class MasterDataFieldAddedHandler {

    @TransactionalEventListener
    public void handle(MasterDataFieldAddedEvent event) {
        log.info("主数据字段添加事件: entityId={}, fieldId={}, name={}",
                event.entityId().value(), event.fieldId().value(), event.name().value());
        // 这里可以添加业务逻辑
    }
}
