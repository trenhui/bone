package com.bone.system.application.port.out;

import com.bone.system.domain.model.alert.AlertRecord;
import com.bone.system.domain.model.alert.AlertRule;

/**
 * 告警通知出站端口：把新触发的告警事件外送到通知体系（E-10.2）。
 *
 * <p><b>为什么需要它</b>：规则 + 事件两张表只是存储形态；不外送，「告警」就只是表格里的一行—— 没人会盯着监控页面。 底层通道（钉钉 / 邮件 / 短信）由
 * bone-notification 的 {@code AlertService} 按全局渠道策略路由， 本端口只负责回答 「触发了什么」，不回答「怎么送」。
 *
 * <p><b>best-effort 契约</b>：实现必须自行容错——通知失败只记日志，不回抛。告警生命周期（触发 / 更新 /
 * 恢复）已完整落库，通知是旁路增强；让一条短信发送失败炸掉整个评估循环是本末倒置。
 */
public interface AlertNotifierPort {

  /**
   * 发送一条「新触发」的告警通知。
   *
   * <p>只在<b>新建</b> TRIGGERED 事件时调用；进行中事件的实测值更新不重复通知（去重，防刷屏）。
   *
   * @param rule 触发告警的规则（含级别与通知渠道偏好）
   * @param record 新落库的 TRIGGERED 事件（含实测值、阈值与消息文案）
   */
  void notifyTriggered(AlertRule rule, AlertRecord record);
}
