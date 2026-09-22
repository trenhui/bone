package com.bone.masterdata.application.event;

import com.bone.masterdata.domain.model.entity.event.MasterDataEntityCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class MasterDataEntityCreatedHandler {

  @TransactionalEventListener
  public void handle(MasterDataEntityCreatedEvent event) {
    log.info("主数据实体创建成功: ID={}, 名称={}", event.entityId(), event.entityName());
  }
}
