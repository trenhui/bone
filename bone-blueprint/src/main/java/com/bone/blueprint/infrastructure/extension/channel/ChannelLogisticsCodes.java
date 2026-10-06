package com.bone.blueprint.infrastructure.extension.channel;

import java.util.Map;

/**
 * 内部物流公司名 → 各渠道的物流公司编码。
 *
 * <p><b>为什么需要它</b>：内部只认「顺丰速运」这样的公司名，而各渠道要求各自的编码体系 （淘宝 {@code SF}、京东 {@code JD_007}、抖音数字码 {@code
 * 1}、拼多多 {@code PDD_SF}）。 这份映射原本在 4 个 {@code *LogisticsExtension} 里各写了一遍 switch，
 * 现在收敛到一处，避免「新增一个承运商要改 4 个地方、漏改一处渠道侧直接查不到物流」。
 *
 * <p><b>为什么用「未识别则拒绝」而不是「兜底成顺丰」</b>：把未知承运商默默映射成顺丰， 会让渠道按错误的承运商去投递，包裹实际由另一家承运，轨迹与时效全错，且错得没有任何日志线索。
 * 故未知承运商一律返回 {@code null}，由调用方明确失败。
 */
final class ChannelLogisticsCodes {

  private ChannelLogisticsCodes() {}

  /** 内部公司名（中文/英文常见写法） → 通用键。 */
  private static final Map<String, String> ALIASES =
      Map.ofEntries(
          Map.entry("顺丰速运", "SF"),
          Map.entry("顺丰", "SF"),
          Map.entry("SF", "SF"),
          Map.entry("SF EXPRESS", "SF"),
          Map.entry("中通快递", "ZTO"),
          Map.entry("中通", "ZTO"),
          Map.entry("ZTO", "ZTO"),
          Map.entry("圆通速递", "YTO"),
          Map.entry("圆通", "YTO"),
          Map.entry("YTO", "YTO"),
          Map.entry("申通快递", "STO"),
          Map.entry("申通", "STO"),
          Map.entry("STO", "STO"),
          Map.entry("韵达快递", "YD"),
          Map.entry("韵达", "YD"),
          Map.entry("YD", "YD"),
          Map.entry("京东物流", "JDL"),
          Map.entry("JDL", "JDL"),
          Map.entry("菜鸟裹裹", "CN"),
          Map.entry("菜鸟", "CN"));

  private static final Map<String, Map<String, String>> BY_CHANNEL =
      Map.of(
          "TAOBAO",
              Map.of(
                  "SF", "SF", "ZTO", "ZTO", "YTO", "YTO", "STO", "STO", "YD", "YD", "JDL", "JDL",
                  "CN", "CN"),
          "JD",
              Map.of(
                  "SF", "JD_007", "ZTO", "JD_002", "YTO", "JD_003", "STO", "JD_004", "YD", "JD_005",
                  "JDL", "JD_001", "CN", "JD_006"),
          "DOUYIN",
              Map.of(
                  "SF", "1", "ZTO", "2", "YTO", "3", "STO", "4", "YD", "5", "JDL", "6", "CN", "7"),
          "PDD",
              Map.of(
                  "SF", "PDD_SF", "ZTO", "PDD_ZTO", "YTO", "PDD_YTO", "STO", "PDD_STO", "YD",
                  "PDD_YD", "JDL", "PDD_JDL", "CN", "PDD_CN"));

  /**
   * 取指定渠道的物流公司编码。
   *
   * @param channelCode 渠道码
   * @param logisticsCompany 内部物流公司名
   * @return 渠道侧编码；<b>承运商未登记时返回 {@code null}</b>（调用方必须显式失败，不得兜底）
   */
  static String codeOf(String channelCode, String logisticsCompany) {
    if (channelCode == null || logisticsCompany == null || logisticsCompany.isBlank()) {
      return null;
    }
    String key = ALIASES.get(logisticsCompany.trim().toUpperCase());
    if (key == null) {
      key = ALIASES.get(logisticsCompany.trim());
    }
    if (key == null) {
      return null;
    }
    return BY_CHANNEL.getOrDefault(channelCode, Map.of()).get(key);
  }
}
