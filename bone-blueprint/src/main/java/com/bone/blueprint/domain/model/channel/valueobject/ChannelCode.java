package com.bone.blueprint.domain.model.channel.valueobject;

import java.util.Arrays;
import java.util.Locale;

/**
 * 销售渠道码枚举（多渠道交易域的第一维度）。
 *
 * <p><b>与 {@code Order.channelSource} 的区别</b>：{@code channelSource} 是<strong>下单终端</strong> （WEB /
 * APP / MINI，来自字典 source_channel），回答「用户从哪儿点的」；{@code ChannelCode} 是 <strong>销售渠道</strong>（淘宝 / 京东 /
 * 抖音 / 拼多多），回答「订单从哪个平台来的」。 一笔抖音订单可能由 APP 终端下单——二者正交，不可合并成一列。
 *
 * <p><b>新增渠道的成本</b>：加枚举 + 加扩展实现（{@code @Extension(tags = "channel=XXX")}）+
 * 加渠道种子数据。业务编排代码不需要改动——这是扩展点机制的核心收益。
 */
public enum ChannelCode {
  TAOBAO("淘宝"),
  JD("京东"),
  DOUYIN("抖音"),
  PDD("拼多多");

  private final String displayName;

  ChannelCode(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }

  /** 宽松解析：忽略大小写与下划线，未识别返回 {@code null}（由调用方决定是否报错）。 */
  public static ChannelCode parseOrNull(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    return Arrays.stream(values())
        .filter(c -> c.name().equals(normalized))
        .findFirst()
        .orElse(null);
  }

  /** 是否为已接入的渠道（用于入参校验）。 */
  public static boolean isSupported(String raw) {
    return parseOrNull(raw) != null;
  }
}
