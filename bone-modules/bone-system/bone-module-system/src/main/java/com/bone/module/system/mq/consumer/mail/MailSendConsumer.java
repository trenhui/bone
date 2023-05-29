package com.bone.module.system.mq.consumer.mail;

import com.bone.module.system.mq.message.mail.MailSendMessage;
import com.bone.module.system.service.mail.MailSendService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.function.Consumer;

/**
 * 针对 {@link MailSendMessage} 的消费者
 *
 * @author 芋道源码
 */
@Component
@Slf4j
public class MailSendConsumer implements Consumer<MailSendMessage> {

    @Resource
    private MailSendService mailSendService;

    @Override
    public void accept(MailSendMessage message) {
        log.info("[accept][消息内容({})]", message);
        mailSendService.doSendMail(message);
    }

}
