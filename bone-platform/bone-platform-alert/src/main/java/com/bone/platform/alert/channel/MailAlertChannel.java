package com.bone.platform.alert.channel;

import com.bone.platform.alert.*;
import com.bone.platform.alert.autoconfigure.AlertProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Slf4j
public class MailAlertChannel implements AlertChannel {
    private final AlertProperties.MailConfig config;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public MailAlertChannel(AlertProperties.MailConfig config,
                            JavaMailSender mailSender) {
        this.config = config;
        this.mailSender = mailSender;
        this.templateEngine = createTemplateEngine();
    }

    @Override
    public AlertChannelType channelType() {
        return AlertChannelType.EMAIL;
    }

    @Override
    public void send(AlertMessage message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

            // 设置基础信息
            helper.setFrom(config.getFrom());
            helper.setTo(config.getRecipients().toArray(new String[0]));
            helper.setSubject(buildSubject(message));

            // 构建邮件内容
            String content = renderTemplate(message);
            helper.setText(content, true); // HTML格式

            // 添加优先级头
            mimeMessage.addHeader("X-Priority", String.valueOf(getPriority(message.getLevel())));

            mailSender.send(mimeMessage);
            log.debug("[Alert] Email alert sent successfully: {}", message.getId());
        } catch (MessagingException e) {
            throw new AlertException("Failed to construct email message", e);
        }
    }

    private String buildSubject(AlertMessage message) {
        return String.format("[%s] %s",
                message.getLevel().name(),
                message.getTitle() != null ? message.getTitle() : "System Alert");
    }

    private String renderTemplate(AlertMessage message) {
        try {
            Context context = new Context();
            context.setVariable("message", message);
            context.setVariable("config", config);
            return templateEngine.process(config.getTemplate(), context);
        } catch (Exception e) {
            log.warn("Failed to render email template, using default", e);
            return buildFallbackContent(message);
        }
    }

    private String buildFallbackContent(AlertMessage message) {
        return String.format("""
            <h3>Alert Details</h3>
            <p><strong>Level:</strong> %s</p>
            <p><strong>Time:</strong> %s</p>
            <pre>%s</pre>
            """,
                message.getLevel(),
                message.getTimestamp().toString(),
                message.getContent());
    }

    private int getPriority(AlertLevel level) {
        return switch (level) {
            case CRITICAL -> 1; // Highest priority
            case HIGH -> 2;
            case MEDIUM -> 3;
            default -> 4;
        };
    }

    private TemplateEngine createTemplateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(new ClassLoaderTemplateResolver(){
            {
                setPrefix("classpath:/templates/alert/");
                setSuffix(".html");
                setTemplateMode(TemplateMode.HTML);
            }
        });
        return engine;
    }
}