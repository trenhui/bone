package com.bone.claim.application;

import com.bone.claim.application.port.out.AiEvalSamplePort;
import com.bone.claim.domain.model.evaluation.AiEvalSampleCollectedEvent;
import com.bone.claim.domain.model.inspection.InspectionDiffType;
import com.bone.claim.domain.model.inspection.InspectionResult;
import com.bone.claim.domain.model.inspection.event.InspectionConcludedEvent;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.tenant.context.TenantContextRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 后督差异回流记录器（学习层，§7.6）。
 *
 * <p>把人工复核的 DIFF 结论转成结构化评测样本事件。此前 {@code diff_type} 只落库不回流， 系统「从每一次人工干预中学习」缺最后一环；本记录器补上「差异 →
 * 改进回路」的触发器。
 *
 * <p><b>一期边界</b>：只投递样本事件，不改案件状态、不改已赔付金额（§6.1 质检态 30/31/32 仍属 V2）， 也不直接改模型/规则；评测集消费、独立盲标与阈值校准属 Phase
 * B。 投递失败只记结构化告警，绝不回滚已提交的抽检结论。
 */
@NoDomainEvent
@Slf4j
@Component
@RequiredArgsConstructor
public class InspectionFeedbackRecorder {

  private final AiEvalSamplePort aiEvalSamplePort;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInspectionConcluded(InspectionConcludedEvent event) {
    if (event.result() != InspectionResult.DIFF
        || event.diffType() == null
        || event.diffType() == InspectionDiffType.NONE) {
      return;
    }
    try {
      // ADR-0031 D3：AFTER_COMMIT 后线程上下文已清空，出站动作须显式声明租户。
      // 一期适配器只发领域事件，但二期一旦换成落库 / 发 MQ 的实现，缺这层声明就会静默跨租户。
      TenantContextRunner.runAs(
          event.tenantId(), () -> aiEvalSamplePort.collect(AiEvalSampleCollectedEvent.from(event)));
    } catch (Exception e) {
      log.error(
          "[FEEDBACK_WRITE_FAILED] claimId={} inspectionNo={} diffType={}",
          event.claimId(),
          event.inspectionNo(),
          event.diffType(),
          e);
    }
  }
}
