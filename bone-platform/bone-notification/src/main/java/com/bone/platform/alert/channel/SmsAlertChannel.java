package com.bone.platform.alert.channel;

import com.bone.platform.alert.AlertMessage;
import com.bone.platform.alert.SmsService;
import com.bone.platform.alert.autoconfigure.AlertProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(value = "alert.channels.sms.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SmsAlertChannel {
    private final SmsService smsService;
    private final AlertProperties.SmsConfig config;

    /**
     * 构造函数，用于将 SmsService 和 SmsConfig 注入到 SmsAlertChannel
     * @param config SmsConfig 配置对象
     * @param smsService SmsService 服务对象
     */
    public SmsAlertChannel(AlertProperties.SmsConfig config, SmsService smsService) {
        this.smsService = smsService;
        this.config = config;
    }

    /**
     * 发送短信方法
     *
     * @param message AlertMessage 消息对象，包含要发送的内容和等级
     */
    public void send(AlertMessage message) {
        // 格式化短信内容，包含消息的等级和具体内容
        String content = "[%s] %s".formatted(message.getLevel(), message.getContent());

        // 遍历配置中的手机号码，逐个发送短信
        config.getPhoneNumbers().forEach(phone -> {
            try {
                // 使用 SmsService 发送短信
                smsService.sendSms(phone, config.getTemplateId(),
                        Map.of("code", message.getLevel().name(), "message", content));

                // 记录成功发送的日志
                log.info("短信已成功发送，手机号: {}, 内容: {}", phone, content);

            } catch (Exception e) {
                // 发送失败时，记录失败日志
                log.error("短信发送失败，手机号: {}", phone, e);
            }
        });
    }
}
