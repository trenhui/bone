package com.bone.claim.application.port.out;

import java.util.Map;

/**
 * 预警通知端口（出站，对接 bone-ai C9 提醒预警）。
 *
 * <p>反欺诈告警 / 阈值预警 / SLA 超时升级统一经此出口（Webhook + HMAC），零新第三方依赖。 一期本地桩仅在日志落告警；二期接统一触达闭环（消警守卫）。
 */
public interface NotificationPort {

  void alert(
      String level,
      String topic,
      ClaimContextPort.ClaimContextView ctx,
      Map<String, Object> detail);
}
