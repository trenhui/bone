package com.bone.claim.domain.model.evidence.event;

import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/**
 * 案件证据集已整体锁定（一次锁定只发一条，案件级）。
 *
 * <p>与逐份材料的 {@link EvidenceLockedEvent} 分工：后者是聚合内每份证据的审计事件， 本事件是「可以开始决策」的业务时点。自动化链路联动只订阅本事件，避免 N
 * 份材料 触发 N 次决策图执行（每案仅一个活跃执行，重复触发会撞唯一约束）。
 */
public record EvidenceSetLockedEvent(
    Long claimId, Long tenantId, int evidenceCount, String operatorId, Instant occurredAt)
    implements DomainEvent {}
