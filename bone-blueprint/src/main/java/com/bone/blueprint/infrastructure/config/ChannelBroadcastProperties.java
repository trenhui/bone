package com.bone.blueprint.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 渠道库存广播中继配置。
 *
 * <p><b>默认全绿（enabled=true、batchSize=50、interval=5s）</b>：库存广播是<strong>正确性要求</strong>（渠道库存落后 = 超卖），
 * 不是可选增强。若默认关闭，漏配一次就静默失去超卖防护且毫无告警——这比多几次重试的风险大得多。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "bone.blueprint.channel.broadcast")
public class ChannelBroadcastProperties {

  /** 是否启用中继。 */
  private boolean enabled = true;

  /** 每轮抢占条数。过大会在渠道限流时放大失败面。 */
  private int batchSize = 50;

  /** 调度间隔（毫秒）。 */
  private long intervalMs = 5000L;

  /** 单条任务最大重试次数，超过转死信待人工处理。 */
  private int maxRetry = 5;

  /** 退避基数（毫秒）：第 n 次重试等待 {@code baseBackoffMs * 2^(n-1)}。 */
  private long baseBackoffMs = 10_000L;

  /** 退避上限（毫秒），防止指数退避跑到小时级导致库存长期不同步。 */
  private long maxBackoffMs = 300_000L;

  /** PROCESSING 卡死判定阈值（毫秒）：超过则认为上次中继崩溃，回退为 PENDING。 */
  private long stuckTimeoutMs = 120_000L;
}
