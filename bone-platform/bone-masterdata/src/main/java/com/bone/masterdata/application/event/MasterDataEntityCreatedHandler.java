package com.bone.masterdata.application.event;

import com.bone.masterdata.domain.model.entity.event.MasterDataEntityCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MasterDataEntityCreatedHandler {
    public void handle(MasterDataEntityCreatedEvent event) {
        log.info("主数据实体创建成功: ID={}, 名称={}", event.entityId(), event.entityName());
        // 可以在这里添加额外的处理逻辑，比如发送通知、记录审计日志等
    }
}