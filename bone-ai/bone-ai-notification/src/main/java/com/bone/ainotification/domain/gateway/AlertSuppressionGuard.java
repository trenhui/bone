package com.bone.ainotification.domain.gateway;

import com.bone.ainotification.domain.model.AlertEvent;
import java.time.Instant;

/**
 * 告警风暴抑制端口（同类告警去重 / 静默，业界运维标配：Alertmanager 分组静默 / PagerDuty 去重）。
 *
 * <p>TPA 真实痛点：同一风控规则被大量赔案同时触发时，逐条外发会造成告警轰炸 → 值班疲劳、真正信号被淹没。 本端口按「同类告警 + 时间窗口 + 条数上限」抑制：窗口内前 {@code
 * N} 条放行（让人知道在发生），超额静默。
 *
 * <p>判定纯函数式（时刻由调用方注入，便于单测穷举）；默认 {@code NoopAlertSuppressionGuard}（不抑制），装配层按配置启用。 多副本部署下内存实现为
 * 进程内（与配额同理），需要全局静默时以 {@code @Bean} 覆盖为共享存储实现。
 */
@FunctionalInterface
public interface AlertSuppressionGuard {

  /**
   * 该告警是否应被静默（不投递）。
   *
   * @param event 告警事件
   * @param now 当前时刻（调用方注入，保证可测）
   * @return true = 静默不投递；false = 放行投递
   */
  boolean shouldSuppress(AlertEvent event, Instant now);
}
