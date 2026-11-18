package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;

/**
 * 推进 Vendor 交易付款资格状态的入站命令。
 *
 * <p><b>只承载「对哪个主体做什么事件、事件来源是谁」</b>：开通闸门五项与停用前置两项由 {@code VendorGateFactsPort} 提供， 解除付款冻结的复核留痕由
 * {@code PaymentBlockReleaseReviewPort} 提供，操作人取登录态。命令不再携带任何事实声明字段 （2026-11-16 独立审查 C-01 修复）。
 *
 * <p>{@code source} 必须是 {@code DOMAIN} 或 {@code EXTERNAL_FINANCE}：付款冻结的提起与解除都只接受后者（设计 D7）。
 */
@Data
public class TransitionVendorCommand {

  @NotNull private Long tenantId;

  /** 统一身份；HTTP 入口由路径提供，命令体可省略。 */
  private String partyId;

  /** spec 契约事件码，如 {@code activation_passed}。 */
  @NotBlank private String event;

  /** 命令来源：{@code DOMAIN} / {@code EXTERNAL_FINANCE}。 */
  @NotBlank private String source;

  private Instant occurredAt;
}
