package com.bone.module.system.mq.consumer.sms;

import com.bone.module.system.mq.message.sms.SmsChannelRefreshMessage;
import com.bone.module.system.service.sms.SmsChannelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 针对 {@link SmsChannelRefreshMessage} 的消费者
 *
 * @author 芋道源码
 */
@Component
@Slf4j
public class SmsChannelRefreshConsumer {

    @Resource
    private SmsChannelService smsChannelService;

    @EventListener
    public void execute(SmsChannelRefreshMessage message) {
        log.info("[execute][收到 SmsChannel 刷新消息]");
        smsChannelService.initLocalCache();
    }

}
