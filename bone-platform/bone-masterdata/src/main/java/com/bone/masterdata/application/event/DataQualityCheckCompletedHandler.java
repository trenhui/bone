package com.bone.masterdata.application.event;

import com.bone.masterdata.domain.model.quality.event.QualityCheckCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DataQualityCheckCompletedHandler {
    public void handle(QualityCheckCompletedEvent event) {
        log.info("数据质量检查完成: 检查ID={}, 实体ID={}, 总记录数={}, 失败记录数={}", 
                event.checkId(), event.entityId(), event.totalRecords(), event.failedRecords());
        // 可以在这里添加额外的处理逻辑，比如生成质量报告、发送告警等
    }
}