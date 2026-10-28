package com.bone.claim.domain.model.inspection.event;

import com.bone.claim.domain.model.inspection.InspectionAction;
import com.bone.claim.domain.model.inspection.InspectionDiffType;
import com.bone.claim.domain.model.inspection.InspectionResult;
import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/**
 * 后督抽检已结论（{@code CONCLUDED}）——审计证据落库事件。
 *
 * <p><b>一期消费方</b>：无（{@code actionTaken}/{@code targetStatus} 只登记处置意图）。<b>V2 消费方</b>：质检差异回流编排（§6.1
 * 质检态 30/31/32 → 重算/退款），届时经 Outbox 订阅本事件，而不是让抽检聚合直接改案件状态。
 */
public record InspectionConcludedEvent(
    Long inspectionId,
    Long tenantId,
    String inspectionNo,
    Long claimId,
    InspectionResult result,
    InspectionDiffType diffType,
    InspectionAction action,
    String targetStatus,
    String inspectorId,
    Instant occurredAt)
    implements DomainEvent {}
