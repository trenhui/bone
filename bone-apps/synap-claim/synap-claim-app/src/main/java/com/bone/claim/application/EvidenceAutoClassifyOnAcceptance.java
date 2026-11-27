package com.bone.claim.application;

import com.bone.claim.domain.model.claim.event.ClaimSubmittedEvent;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.tenant.context.TenantContextRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 签收时对案件已有影像做一次兜底自动分类（A1 受理签收 → A2 材料聚合）。
 *
 * <p><b>时序说明</b>：报案受理签收（DRAFT→ACCEPTED，{@link ClaimSubmittedEvent}）通常发生在影像上传之前，
 * 此时案件还没有材料，本监听器是空操作；真正的主分类在材料登记时由 {@code EvidenceController.register} 用 「文件名 +
 * 前端上传的版式信号」完成。本监听器只覆盖"先有证据后签收"的场景（如 TPA 存量导入）， 且由 {@link
 * EvidenceApplicationService#autoClassifyOnAcceptance} 严格限制为仅处理 OTHER 证据——
 * 不会用缺失的版式信号把已正确分类的签名图打回发票/其他。
 *
 * <p>照抄 {@code ClaimStakeholderRecorder} 的 AFTER_COMMIT + 显式租户上下文范式；分类失败不回滚签收主流程。
 */
@NoDomainEvent
@Slf4j
@Component
@RequiredArgsConstructor
public class EvidenceAutoClassifyOnAcceptance {

  private final EvidenceApplicationService evidenceApplicationService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onClaimSubmitted(ClaimSubmittedEvent event) {
    try {
      TenantContextRunner.runAs(
          event.tenantId(),
          () -> {
            int updated = evidenceApplicationService.autoClassifyOnAcceptance(event.claimId());
            if (updated > 0) {
              log.info(
                  "[EVIDENCE_AUTO_CLASSIFY_ON_ACCEPT] claimId={} reclassified={}",
                  event.claimId(),
                  updated);
            }
          });
    } catch (Exception e) {
      // 兜底分类失败不回滚已提交的签收；主分类仍在登记时完成，此处仅记录供监控。
      log.error(
          "[EVIDENCE_AUTO_CLASSIFY_FAILED] claimId={} reason={}", event.claimId(), e.getMessage());
    }
  }
}
