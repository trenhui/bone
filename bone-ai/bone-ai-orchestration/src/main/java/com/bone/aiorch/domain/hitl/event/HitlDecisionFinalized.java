package com.bone.aiorch.domain.hitl.event;

import com.bone.aiorch.domain.hitl.HitlItem;
import com.bone.core.domain.DomainEvent;
import java.time.LocalDateTime;

/**
 * HITL 人工终审决策回流事件：人工裁决到达<b>业务终态</b>（confirm / reject / modify）时发布， 供产品侧业务状态机订阅并推进对应业务动作（赔付 / 拒赔 /
 * 按改后值执行、冻结或释放直付额度等）。
 *
 * <p><b>为什么用领域事件解耦（回流契约）</b>：平台恪守「只做证明与治理、不做判断与决策」——HITL 产出裁决与证据链， 但「推进赔案状态 /
 * 动钱」是业务动作，属产品域状态机。以事件而非直接调用传递，平台无需知晓业务状态机长什么样；产品按 {@link #scene()}（如 {@code
 * claim-<claimId>}）订阅自家里程碑动作即可。业界对位即 pk-tpa-saas「理算结果 → confirmed → 赔案状态推进 + 结果锁定」。
 *
 * <p><b>escalate 不发布本事件</b>：升级是「改人工全程处理、仍在审」，并非业务终态决策，业务状态机不应据此推进。
 *
 * @param tenantId 租户
 * @param itemId HITL 项 id（回放关联）
 * @param scene 业务关联场景（产品据此路由到对应业务对象）
 * @param action 裁决动作 confirm / reject / modify
 * @param value modify 的改后值（其余为 null）
 * @param reasonType 回流原因（ReasonType 名，confirm 可为 null）
 * @param actor 裁决人
 * @param finalStatus 终态 CONFIRMED / REJECTED / MODIFIED
 * @param traceId 证据链关联键（与 LLM 调用回放同一 traceId）
 * @param occurredAt 裁决时刻
 */
public record HitlDecisionFinalized(
    String tenantId,
    String itemId,
    String scene,
    String action,
    String value,
    String reasonType,
    String actor,
    String finalStatus,
    String traceId,
    LocalDateTime occurredAt)
    implements DomainEvent {

  /** 从已终态 HITL 项 + 末条处置构造回流事件。 */
  public static HitlDecisionFinalized of(HitlItem item, HitlItem.Disposition disposition) {
    return new HitlDecisionFinalized(
        item.getTenantId(),
        item.getId(),
        item.getScene(),
        disposition.action(),
        disposition.value(),
        disposition.reasonType(),
        disposition.actor(),
        item.getStatus().name(),
        item.getTraceId(),
        disposition.actedAt());
  }
}
