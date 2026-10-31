package com.bone.claim.domain.model.policy.event;

import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/**
 * 保单规则开关变更事件（启用/停用）。
 *
 * <p><b>为什么这是领域事件而不是"改个字段"</b>：保单规则的启用/停用直接决定 "这张保单今天还能不能收案"——它影响报案受理、额度年度裁决、乃至上游保司系统的
 * 状态同步。这类事实若只落在库里，外部系统无从感知"规则刚被停用了"， 而按本仓的<b>事实表/投影</b>纪律（额度流水是事实、账户是投影），"发生过什么"
 * 应当以事件/流水的形式可被追溯与订阅。
 *
 * <p>事件语义只描述"发生了什么"（from → to），不描述"接下来该做什么"—— 后者由编排层（application/infrastructure）决定，避免把流程耦合进领域事件。
 */
public record PolicyConfigStatusChangedEvent(
    Long policyId,
    Long tenantId,
    String policyNo,
    String fromStatus,
    String toStatus,
    String operatorId,
    Instant occurredAt)
    implements DomainEvent {}
