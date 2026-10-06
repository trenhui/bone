package com.bone.blueprint.adapter.schedule;

import com.bone.blueprint.application.port.out.ChannelBroadcastRelayPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 渠道库存广播中继定时任务（adapter/schedule）。
 *
 * <p>与 {@code OrderOutboxRelayJob} 同构：adapter 直接依赖技术出站端口， 不绕 application 中转（E-5.3 禁纯技术轮询中转层）。
 *
 * <p><b>固定延迟而非固定频率</b>：{@code fixedDelay} 保证「上一轮没跑完不会叠加下一轮」。 渠道限流时投递会变慢，
 * 固定频率会在上一轮还在重试时就发起新一轮，把限流放大成雪崩。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelBroadcastRelayJob {

  private final ChannelBroadcastRelayPort channelBroadcastRelayPort;

  @Scheduled(fixedDelayString = "${bone.blueprint.channel.broadcast.interval-ms:5000}")
  public void relay() {
    try {
      int sent = channelBroadcastRelayPort.relayPending();
      if (sent > 0) {
        log.info("渠道库存广播中继完成: sent={}", sent);
      }
    } catch (RuntimeException ex) {
      // 不让异常冒泡：@Scheduled 任务抛异常会被调度器记录但可能中断后续调度，
      // 而广播是「必须持续推进」的日常任务，宁可失败留日志也不能停摆。
      log.error("渠道库存广播中继异常（本轮跳过，下轮继续）", ex);
    }
  }
}
