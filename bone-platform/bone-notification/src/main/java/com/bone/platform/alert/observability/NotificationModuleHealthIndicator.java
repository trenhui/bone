package com.bone.platform.alert.observability;

import com.bone.platform.alert.autoconfigure.AlertAutoConfiguration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * 通知/告警模块健康检查占位实现。
 *
 * <p>说明：bone-notification 为 SDK/Autoconfigure 库（宿主服务通常为 bone-integration），因此本类使用 {@link
 * ConditionalOnClass Actuator} 侧条件，仅在宿主引入 {@code spring-boot-starter-actuator} 时生效。
 *
 * <p>已知限制（技术债，待后续扩展）：
 *
 * <ul>
 *   <li>当前仅返回静态 UP，未真正校验：邮件 SMTP 握手、钉钉 webhook 响应、阿里云短信签名鉴权、 站内信（InApp）写入通道健康；
 *   <li>未聚合通知异步线程池队列深度、拒绝策略触发次数；
 *   <li>后续应拆分为 {@code alert-mail / alert-dingtalk / alert-sms / alert-inapp} 子 HealthIndicator，并把最近
 *       N 次发送失败率写入 {@code down} 分支。
 * </ul>
 */
@Component
@ConditionalOnClass(AlertAutoConfiguration.class)
public class NotificationModuleHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    return Health.up()
        .withDetail("module", "bone-notification")
        .withDetail("status", "STUB")
        .withDetail(
            "limitation",
            "NotificationModuleHealthIndicator is a placeholder; real mail/dingtalk/sms/inapp checks pending")
        .build();
  }
}
