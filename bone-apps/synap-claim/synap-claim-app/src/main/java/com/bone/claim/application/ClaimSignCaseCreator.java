package com.bone.claim.application;

import com.bone.claim.domain.model.sign.SignBatchItem;
import com.bone.claim.domain.model.sign.event.SignStatusChangedEvent;
import com.bone.claim.domain.model.sign.valueobject.SignCaseOutcome;
import com.bone.claim.domain.model.sign.valueobject.SignStatus;
import com.bone.claim.domain.repository.ClaimSignBatchRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.tenant.context.TenantContextRunner;
import com.bone.core.util.DistributedIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 签收确认后的自动建案订阅者：签收已提交，建案失败只降级人工，不回滚签收事实。 */
@NoDomainEvent
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimSignCaseCreator {

  private final ClaimSignBatchRepository batchRepository;
  private final ClaimSignCasePipeline pipeline;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onSignFinished(SignStatusChangedEvent event) {
    if (event.toStatus() != SignStatus.FINISHED) {
      return;
    }
    TenantContextRunner.runAs(
        event.tenantId(),
        () -> {
          try {
            for (SignBatchItem item : batchRepository.findItemsByBatchId(event.batchId())) {
              processOne(event, item);
            }
          } catch (Exception e) {
            log.error("签收自动建案批次读取失败: batchId={}", event.batchId(), e);
            appendTrack(event, null, null, "AUTO_CASE_FAILED", "批次明细读取失败：" + describe(e));
          }
        });
  }

  private void processOne(SignStatusChangedEvent event, SignBatchItem item) {
    long tenantId = event.tenantId();
    long batchId = event.batchId();
    long itemId = item.getId() == null ? 0L : item.getId();
    String stage = "建案";
    try {
      ClaimSignCaseCreation created = pipeline.create(tenantId, batchId, item);
      if (created.outcome() != SignCaseOutcome.NEW) {
        String action =
            created.outcome() == SignCaseOutcome.SKIPPED ? "AUTO_CASE_SKIPPED" : "AUTO_CASE_MANUAL";
        appendTrack(event, itemId, created.claimId(), action, created.message());
        return;
      }
      stage = "分配初审";
      pipeline.allocate(tenantId, created.claimId());
      stage = "启动初审";
      pipeline.startPreExam(tenantId, created.claimId());
      batchRepository.markCaseForItem(itemId, tenantId, SignBatchItem.CASE_NEW);
      appendTrack(event, itemId, created.claimId(), "AUTO_CASE_CREATED", created.message());
    } catch (SignCaseDuplicateException duplicate) {
      batchRepository.linkCaseForItem(
          itemId, tenantId, duplicate.getWinnerClaimId(), SignBatchItem.CASE_SKIPPED);
      item.linkCase(duplicate.getWinnerClaimId(), SignBatchItem.CASE_SKIPPED);
      appendTrack(
          event,
          itemId,
          duplicate.getWinnerClaimId(),
          "AUTO_CASE_SKIPPED",
          "并发幂等命中，沿用首次建案结果 " + duplicate.getWinnerClaimNo());
    } catch (Exception e) {
      log.error("签收自动建案失败: batchId={}, itemId={}, stage={}", batchId, itemId, stage, e);
      batchRepository.markCaseForItem(itemId, tenantId, SignBatchItem.CASE_FAILED);
      item.markSkipped(SignBatchItem.CASE_FAILED);
      appendTrack(event, itemId, null, "AUTO_CASE_FAILED", stage + "失败转人工：" + describe(e));
    }
  }

  private static String describe(Exception e) {
    String message = e.getMessage() == null ? "" : e.getMessage();
    String reason = e.getClass().getSimpleName() + ": " + message;
    return reason.length() > 200 ? reason.substring(0, 200) : reason;
  }

  private void appendTrack(
      SignStatusChangedEvent event, Long itemId, Long claimId, String action, String message) {
    String detail = itemId == null ? message : message + "（明细" + itemId + "）";
    batchRepository.saveTrack(
        DistributedIdGenerator.generateLongId(),
        event.tenantId(),
        event.batchId(),
        claimId,
        SignStatus.FINISHED.code(),
        action,
        detail,
        null,
        "SYSTEM",
        java.time.Instant.now());
  }
}
