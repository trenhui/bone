package com.bone.masterdata.application.event;

import com.bone.masterdata.domain.model.entity.event.MasterDataEntityPublishedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class MasterDataEntityPublishedHandler {

  @TransactionalEventListener
  public void handle(MasterDataEntityPublishedEvent event) {
    log.info("主数据实体发布事件: id={}", event.entityId());
    // 这里可以添加业务逻辑，比如同步到其他系统等
  }
}
