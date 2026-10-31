package com.bone.claim.domain.model.policy.event;

import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/**
 * 保障计划状态变更事件（发布/下线）。
 *
 * <p><b>为什么要发</b>：计划发布（{@code DRAFT → ACTIVE}）是一个<b>业务上可见的节点</b>——
 * 它的直接后果是"该计划下的生效责任自此参与理算"，即未来赔案的计算口径变了。 不发事件的话，下游只能靠轮询表状态来感知口径变更，既慢又不可靠。
 *
 * <p>事件只描述事实（from → to + 计划标识），不携带"应触发哪些重算"这类流程指令。
 */
public record PlanStatusChangedEvent(
    Long planId,
    Long tenantId,
    String policyNo,
    String planCode,
    String fromStatus,
    String toStatus,
    String operatorId,
    Instant occurredAt)
    implements DomainEvent {}
