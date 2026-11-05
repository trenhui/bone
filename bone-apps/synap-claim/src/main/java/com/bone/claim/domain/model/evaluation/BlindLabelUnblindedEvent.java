package com.bone.claim.domain.model.evaluation;

import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/**
 * 盲标解盲事件（G4-1 飞轮触发器）。
 *
 * <p>解盲即「人 vs AI」成对样本产出的事实时点：此前该任务在库内没有任何 AI 数据（物理分离）， 解盲事件是评测流水线感知「又一对样本可取」的索引——成对明细留在 {@code
 * t_claim_blind_label_task}，按 {@code taskNo} 回查，事件不复制长文本。
 *
 * <p><b>aiParticipated 的口径</b>：取决策轨迹上最新执行 {@code confidence_source == MODEL}
 * ——证明不了是模型参与（STUB/FALLBACK/UNKNOWN）就不算参与，「桩环境下盲标出来的 AI 一致率」是假证据。
 */
public record BlindLabelUnblindedEvent(
    Long taskId,
    Long tenantId,
    Long claimId,
    String taskNo,
    boolean aiParticipated,
    Instant occurredAt)
    implements DomainEvent {}
