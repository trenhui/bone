package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;

/**
 * 推进 Supplier 合作资格状态的入站命令。
 *
 * <p><b>只承载「对哪个主体做什么事件」</b>（2026-11-16 独立审查 C-01 修复）：退出所需的未结 PO / 合同 / 应付事实 一律由 {@code
 * SupplierExitFactsPort} 提供，准入所需的变更请求批准事实由 {@code SupplierOnboardingFactsPort} 提供；
 * 操作人取登录态。命令里不再保留任何「事实声明」字段 —— 一旦允许调用方声明，守卫就变成被守卫方自证。
 */
@Data
public class TransitionSupplierCommand {

  @NotNull private Long tenantId;

  /**
   * 统一身份。HTTP 入口由路径 {@code /suppliers/{partyId}/...} 提供（路径为唯一真源）， 命令体可省略；直接调用应用服务的场景仍须填写，空值由应用层
   * fail-closed。
   */
  private String partyId;

  /** spec 契约事件码，如 {@code exit_approved}。 */
  @NotBlank private String event;

  private Instant occurredAt;
}
