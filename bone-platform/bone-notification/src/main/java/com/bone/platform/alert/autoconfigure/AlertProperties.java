package com.bone.platform.alert.autoconfigure;

import com.bone.platform.alert.AlertChannelType;
import com.bone.platform.alert.AlertLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "alert")
@Validated
@Data
public class AlertProperties {
  private Map<AlertLevel, ChannelPolicy> policies = new EnumMap<>(AlertLevel.class);
  private Channels channels = new Channels();
  private Async async = new Async();
  private int globalRetry = 3;

  @Data
  @Valid
  public static class Channels {
    private MailConfig mail;
    private DingTalkConfig dingtalk;
    private SmsConfig sms;
  }

  @Data
  @Valid
  public static class MailConfig {
    private boolean enabled = false;
    @NotEmpty private String from;
    @NotEmpty private List<String> recipients;
    private String template = "classpath:/templates/alert-mail.html";
  }

  @Data
  @Valid
  public static class DingTalkConfig {
    private boolean enabled = false;
    @NotEmpty private String webhook;
    private String secret;
    private List<String> atMobiles;
  }

  /**
   * 短信渠道配置。
   *
   * <p><b>必须是 {@code static} 嵌套类</b>：非静态内部类会被编译器插入指向外部实例的合成字段 {@code this$0}，Spring Boot 的 relaxed
   * binding 会把它当成一个待绑定属性，从而抛 {@code InvalidConfigurationPropertyNameException: Configuration
   * property name 'this$0' is not valid}——该失败发生在<strong>容器启动期</strong>，表现为整个应用起不来，而不是某个配置项不生效。 同级的
   * {@link MailConfig} / {@link DingTalkConfig} 都已是 static，此处此前漏了。
   */
  @Data
  public static class SmsConfig {
    private boolean enabled = false;
    private String provider;
    private String apiKey;
    private String apiSecret;
    @NotEmpty private List<String> phoneNumbers;
    private String templateId;
  }

  @Data
  public static class Async {
    private int corePoolSize = 5;
    private int maxPoolSize = 10;
    private int queueCapacity = 1000;
    private int keepAliveSeconds = 60;
  }

  @Data
  public static class ChannelPolicy {
    @NotEmpty private List<AlertChannelType> channels;
    private int retry = 1;
    private Duration timeout = Duration.ofSeconds(5);
  }
}
