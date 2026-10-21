package com.bone.system.infrastructure.notification;

import com.bone.platform.alert.AlertLevel;
import com.bone.platform.alert.AlertMessage;
import com.bone.platform.alert.AlertService;
import com.bone.system.application.port.out.AlertNotifierPort;
import com.bone.system.domain.model.alert.AlertRecord;
import com.bone.system.domain.model.alert.AlertRule;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 告警通知适配器：把 bone-system 的告警事件桥接到 bone-notification 的 {@link AlertService} （bone-integration {@code
 * AlertIntegrationEventNotifier} 的同模块样板）。
 *
 * <p><b>渠道路由不在本类</b>：{@code AlertService} 按 {@code alert.channels.*} 全局策略选通道， 未启用任何渠道时 {@code
 * sendAlert} 是安全空操作（本地/测试环境天然静默）。规则上的 {@code notificationChannels} 是运营偏好， 随消息 context
 * 下发，供通道侧记录与将来做按规则路由时使用——不假装它已经路由到了那些渠道。
 *
 * <p><b>租户</b>：{@code CompositeAlertService} 在调用线程捕获租户随消息下发；评估 Job 以平台租户（0）进入上下文， 故告警通知的 tenantId
 * 恒为 0（平台级监控语义）。
 */
@Slf4j
@Component
public class BoneAlertNotifierAdapter implements AlertNotifierPort {

  private final AlertService alertService;
  private final boolean enabled;

  public BoneAlertNotifierAdapter(
      AlertService alertService,
      @Value("${bone.system.alert.notification-enabled:true}") boolean enabled) {
    this.alertService = alertService;
    this.enabled = enabled;
  }

  @Override
  public void notifyTriggered(AlertRule rule, AlertRecord record) {
    if (!enabled) {
      return;
    }
    try {
      alertService.sendAlert(buildMessage(rule, record));
    } catch (Exception ex) {
      // best-effort：通知失败只记日志，绝不回抛污染告警评估循环（告警事件已完整落库）
      log.error(
          "[AlertNotify] 告警通知发送失败（不影响告警生命周期）: ruleId={}, recordId={}",
          rule.getId(),
          record.getId(),
          ex);
    }
  }

  private static AlertMessage buildMessage(AlertRule rule, AlertRecord record) {
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("module", "system");
    context.put("ruleId", rule.getId());
    context.put("recordId", record.getId());
    context.put("metric", record.getMetricName());
    context.put("actualValue", record.getActualValue());
    context.put("threshold", record.getThreshold());
    if (rule.getNotificationChannels() != null) {
      context.put("channels", rule.getNotificationChannels());
    }
    return AlertMessage.builder()
        .level(toPlatformLevel(rule.getAlertLevel()))
        .title("【告警触发】" + rule.getName())
        .content(record.getMessage())
        .businessId(String.valueOf(record.getId()))
        .context(context)
        .build();
  }

  /**
   * 告警级别映射：CRITICAL→CRITICAL、WARNING→HIGH、INFO→INFO。
   *
   * <p>两边枚举不同名（system 的 WARNING vs notification 的 HIGH/MEDIUM），显式映射而不是 {@code
   * valueOf(level.name())}——后者遇不同名直接炸，且「警告」语义上就是需要立即关注的高优先级。
   */
  static AlertLevel toPlatformLevel(
      com.bone.system.domain.model.alert.valueobject.AlertLevel level) {
    if (level == null) {
      return AlertLevel.HIGH;
    }
    return switch (level) {
      case CRITICAL -> AlertLevel.CRITICAL;
      case WARNING -> AlertLevel.HIGH;
      case INFO -> AlertLevel.INFO;
    };
  }
}
