package com.bone.procurement.readiness;

/**
 * 就绪度运行期事实。布尔用 {@link Boolean} 以表达"取数失败/未知"（{@code null}），未知必须 fail-closed 为 {@link
 * AiReadinessVerdict#UNKNOWN}，不得乐观回落。
 *
 * @param providerConfigured 是否配置真实 provider（null=未知）
 * @param governanceEnabled 治理 Bean 是否装配（false=关闭，不是未知）
 * @param killSwitchEngaged 全域 kill switch 是否生效（null=未知）
 * @param evalFrozen 评测集是否已冻结通过线（null=未知/读取失败；false=未转正）
 * @param inferenceCapabilityRegistered 治理能力注册表中是否存在允许推理的已启用能力
 */
public record AiReadinessFacts(
    Boolean providerConfigured,
    boolean governanceEnabled,
    Boolean killSwitchEngaged,
    Boolean evalFrozen,
    boolean inferenceCapabilityRegistered) {

  public static AiReadinessFacts allReady() {
    return new AiReadinessFacts(true, true, false, true, true);
  }
}
