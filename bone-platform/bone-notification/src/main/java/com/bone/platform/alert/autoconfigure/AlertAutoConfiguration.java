package com.bone.platform.alert.autoconfigure;

import com.bone.platform.alert.AlertChannel;
import com.bone.platform.alert.AlertService;
import com.bone.platform.alert.CompositeAlertService;
import com.bone.platform.alert.SmsService;
import com.bone.platform.alert.channel.DingTalkAlertChannel;
import com.bone.platform.alert.channel.MailAlertChannel;
import com.bone.platform.alert.channel.SmsAlertChannel;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
@EnableConfigurationProperties(AlertProperties.class)
@ConditionalOnClass(AlertService.class)
@AutoConfigureAfter({MailSenderAutoConfiguration.class, WebClientAutoConfiguration.class})
public class AlertAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AlertService alertService(
      AlertProperties properties,
      ObjectProvider<AlertChannel> channels,
      ObjectProvider<RetryTemplate> retryTemplate) {
    return new CompositeAlertService(
        properties,
        channels.orderedStream().collect(Collectors.toList()),
        retryTemplate.getIfAvailable(RetryTemplate::new));
  }

  @Bean
  @ConditionalOnProperty(prefix = "alert.channels.mail", name = "enabled", havingValue = "true")
  public MailAlertChannel mailAlertChannel(AlertProperties properties, JavaMailSender mailSender) {
    return new MailAlertChannel(properties.getChannels().getMail(), mailSender);
  }

  @Bean
  @ConditionalOnProperty(prefix = "alert.channels.dingtalk", name = "enabled", havingValue = "true")
  public DingTalkAlertChannel dingTalkAlertChannel(AlertProperties properties) {
    return new DingTalkAlertChannel(properties.getChannels().getDingtalk());
  }

  @Bean
  @ConditionalOnMissingBean
  public RetryTemplate retryTemplate(AlertProperties properties) {
    RetryTemplate template = new RetryTemplate();
    template.setRetryPolicy(new SimpleRetryPolicy(properties.getGlobalRetry()));
    template.setBackOffPolicy(new ExponentialBackOffPolicy());
    return template;
  }

  @Bean
  @ConditionalOnProperty(prefix = "alert.channels.sms", name = "enabled", havingValue = "true")
  public SmsAlertChannel smsAlertChannel(AlertProperties properties, SmsService smsService) {
    return new SmsAlertChannel(
        properties.getChannels().getSms(), smsService); // 将 SmsService 注入到 SmsAlertChannel 中
  }
}
