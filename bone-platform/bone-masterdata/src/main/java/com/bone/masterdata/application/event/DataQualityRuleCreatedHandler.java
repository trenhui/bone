package com.bone.masterdata.application.event;

import com.bone.masterdata.domain.model.quality.event.DataQualityRuleCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class DataQualityRuleCreatedHandler {

    @TransactionalEventListener
    public void handle(DataQualityRuleCreatedEvent event) {
        log.info("数据质量规则创建事件: entityId={}, ruleId={}, name={}",
                event.entityId(), event.ruleId(), event.ruleName());
        // 这里可以添加业务逻辑
    }
}
