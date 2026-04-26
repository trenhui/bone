package com.bone.tpa.intelligent.adjustment.event.handler;

import com.bone.tpa.intelligent.adjustment.model.ClaimProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 事件监听器类
@Component
@Slf4j
public class ClaimAdjustEventHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleClaimProcessedEvent(ClaimProcessedEvent event) {
        // 处理事件逻辑
        try {
            log.debug("发送理算结果通知，赔案号: {}", event.getRequest().getClaimNo());
            //todo
        } catch (Exception e) {
            log.error("理算结果通知发送失败，赔案号: {}", event.getRequest().getClaimNo(), e);
            // 可添加重试机制
        }
    }
}
