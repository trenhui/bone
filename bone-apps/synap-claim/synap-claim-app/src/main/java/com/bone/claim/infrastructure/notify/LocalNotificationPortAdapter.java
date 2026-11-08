package com.bone.claim.infrastructure.notify;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.NotificationPort;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 预警通知适配器（一期本地桩：仅日志落告警）。
 *
 * <p>二期接 bone-ai C9 统一触达闭环（Webhook + HMAC + 消警守卫），反欺诈告警/阈值预警/SLA 超时升级统一经此出口。
 */
@Component
public class LocalNotificationPortAdapter implements NotificationPort {

  private static final Logger log = LoggerFactory.getLogger(LocalNotificationPortAdapter.class);

  @Override
  public void alert(
      String level,
      String topic,
      ClaimContextPort.ClaimContextView ctx,
      Map<String, Object> detail) {
    log.warn("[alert][{}] topic={} claimId={} detail={}", level, topic, ctx.claimId(), detail);
  }
}
