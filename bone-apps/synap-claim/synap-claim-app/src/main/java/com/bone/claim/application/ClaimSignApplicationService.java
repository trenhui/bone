package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.sign.ClaimSignBatch;
import com.bone.claim.domain.model.sign.SignBatchItem;
import com.bone.claim.domain.model.sign.SignTrackLog;
import com.bone.claim.domain.model.sign.valueobject.SignChannel;
import com.bone.claim.domain.model.sign.valueobject.SignSource;
import com.bone.claim.domain.model.sign.valueobject.SignStatus;
import com.bone.claim.domain.model.sign.valueobject.SignType;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimSignBatchRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 签收应用用例：线上自动签收（双触发）与线下人工批次签收共用同一聚合与状态机。 */
@Service
@RequiredArgsConstructor
public class ClaimSignApplicationService {

  private final ClaimSignBatchRepository batchRepository;
  private final ClaimCaseRepository claimCaseRepository;
  private final TenantPort tenantPort;
  private final DomainEventPublisher domainEventPublisher;

  /** 线上自动签收统一入口：内部报案末端与外部推送回执均调用。businessKey 幂等去重。 */
  @Transactional(propagation = Propagation.REQUIRED)
  public AutoSignResult signOnAcceptance(AutoSignCommand command) {
    long tenantId = tenantPort.currentTenantId();
    if (command.businessKey() == null || command.businessKey().isBlank()) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_SIGN_PUSH_INVALID, "businessKey 缺失");
    }
    ClaimSignBatch existing = batchRepository.findByBusinessKey(tenantId, command.businessKey());
    if (existing != null) {
      return new AutoSignResult(existing.getBatchNo(), existing.getId(), false);
    }
    String batchNo = generateBatchNo();
    ClaimSignBatch batch =
        ClaimSignBatch.create(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            batchNo,
            SignSource.ONLINE,
            SignType.AUTO,
            command.signChannel(),
            command.businessKey(),
            null,
            null,
            null,
            null);
    batch.transitionTo(SignStatus.WAIT_FOR_IMAGE, "AUTO_ACCEPT", "SYSTEM");
    batch.transitionTo(SignStatus.WAIT_FOR_CONFIRMATION, "AUTO_IMAGE_READY", "SYSTEM");
    batch.transitionTo(SignStatus.FINISHED, "AUTO_SIGN", "SYSTEM");
    batch.completeSign("SYSTEM", "线上自动签收，要素校验通过并留痕", Instant.now());
    batchRepository.insert(batch);
    appendTrack(batch, null, "AUTO_SIGN", "线上自动签收完成", "SYSTEM");
    domainEventPublisher.publishFrom(batch);
    return new AutoSignResult(batchNo, batch.getId(), true);
  }

  /** 线下：创建批次。 */
  @Transactional(propagation = Propagation.REQUIRED)
  public long createOfflineBatch(CreateOfflineBatchCommand command) {
    long tenantId = tenantPort.currentTenantId();
    ClaimSignBatch batch =
        ClaimSignBatch.create(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            generateBatchNo(),
            SignSource.OFFLINE,
            SignType.MANUAL,
            command.signChannel(),
            command.businessKey(),
            command.expressCompany(),
            command.expressNo(),
            command.sender(),
            command.arriveTime());
    batchRepository.insert(batch);
    appendTrack(batch, null, "CREATE", "线下批次创建", command.operator());
    domainEventPublisher.publishFrom(batch);
    return batch.getId();
  }

  /** 线下：导入人员名单（生成明细，阶段 SIGNING）。名单为空直接拒绝。 */
  @Transactional(propagation = Propagation.REQUIRED)
  public void importParticipants(long batchId, List<Long> claimIds, String operator) {
    if (claimIds == null || claimIds.isEmpty()) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_SIGN_PARTICIPANTS_EMPTY, "人员名单为空");
    }
    long tenantId = tenantPort.currentTenantId();
    ClaimSignBatch batch = requireBatch(tenantId, batchId);
    if (batch.status() != SignStatus.WAIT_FOR_PARTICIPANT) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_SIGN_STATUS_CONFLICT, "当前状态不允许导入名单");
    }
    for (long claimId : claimIds) {
      ClaimCase claim = claimCaseRepository.findById(claimId);
      if (claim == null) {
        throw ClaimErrors.of(ClaimErrorCodes.CLAIM_NOT_FOUND, claimId);
      }
      batch.addItem(SignBatchItem.of(tenantId, batchId, claimId, claim.getClaimNo()));
    }
    batch.transitionTo(SignStatus.WAIT_FOR_IMAGE, "IMPORT_PARTICIPANTS", operator);
    batchRepository.save(batch);
    appendTrack(batch, null, "IMPORT_PARTICIPANTS", "导入名单 " + claimIds.size() + " 条", operator);
    domainEventPublisher.publishFrom(batch);
  }

  /** 线下：登记影像完备。 */
  @Transactional(propagation = Propagation.REQUIRED)
  public void registerImage(long batchId, String operator) {
    long tenantId = tenantPort.currentTenantId();
    ClaimSignBatch batch = requireBatch(tenantId, batchId);
    if (batch.status() != SignStatus.WAIT_FOR_IMAGE) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_SIGN_STATUS_CONFLICT, "当前状态不允许登记影像");
    }
    batch.registerImageReady();
    if (batch.getImageReadyCount() >= batch.getClaimCount()) {
      batch.transitionTo(SignStatus.WAIT_FOR_CONFIRMATION, "IMAGE_READY", operator);
    }
    batchRepository.save(batch);
    appendTrack(
        batch,
        null,
        "IMAGE_READY",
        "影像已登记 " + batch.getImageReadyCount() + "/" + batch.getClaimCount(),
        operator);
    domainEventPublisher.publishFrom(batch);
  }

  /** 线下：人工二次确认签收。CAS 按前置态，影响行数 0 即冲突。 */
  @Transactional(propagation = Propagation.REQUIRED)
  public void confirmOffline(long batchId, String operator, String remark) {
    long tenantId = tenantPort.currentTenantId();
    ClaimSignBatch batch = requireBatch(tenantId, batchId);
    batch.requireImagesReady();
    batch.completeSign(operator, remark, Instant.now());
    int affected =
        batchRepository.confirmWithCas(
            batchId, tenantId, SignStatus.WAIT_FOR_CONFIRMATION.code(), operator, remark);
    if (affected == 0) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_SIGN_STATUS_CONFLICT, "签收冲突，请刷新后重试");
    }
    // CAS 成功后才推进内存态：轨迹与领域事件须记录 FINISHED，而非前置态；
    // 若在 CAS 前跃迁，脏检查可能抢先落库并让前置态条件更新恒失败。
    batch.transitionTo(SignStatus.FINISHED, "CONFIRM", operator);
    appendTrack(batch, null, "CONFIRM", "线下人工确认签收", operator);
    domainEventPublisher.publishFrom(batch);
  }

  @Transactional(readOnly = true)
  public ClaimSignBatch detail(long batchId) {
    return requireBatch(tenantPort.currentTenantId(), batchId);
  }

  @Transactional(readOnly = true)
  public PageResult<ClaimSignBatch> list(String source, String signStatus, int page, int size) {
    return batchRepository.findBatchPage(
        tenantPort.currentTenantId(), source, signStatus, page, size);
  }

  @Transactional(readOnly = true)
  public List<SignTrackLog> tracks(long batchId) {
    return batchRepository.findTracksByBatchId(batchId);
  }

  private ClaimSignBatch requireBatch(long tenantId, long batchId) {
    ClaimSignBatch batch = batchRepository.findById(batchId);
    if (batch == null || !tenantIdEquals(batch.getTenantId(), tenantId)) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_SIGN_BATCH_NOT_FOUND, batchId);
    }
    return batch;
  }

  private static boolean tenantIdEquals(Long a, long b) {
    return a != null && a == b;
  }

  private void appendTrack(
      ClaimSignBatch batch, Long claimId, String actionType, String message, String operator) {
    SignTrackLog track =
        SignTrackLog.of(
            batch.getTenantId(),
            batch.getId(),
            claimId,
            batch.status(),
            actionType,
            message,
            null,
            operator);
    batchRepository.saveTrack(
        DistributedIdGenerator.generateLongId(),
        batch.getTenantId(),
        batch.getId(),
        claimId,
        track.getSignStatus(),
        actionType,
        message,
        null,
        operator,
        track.getOccurredAt());
  }

  private static String generateBatchNo() {
    return "SG" + DistributedIdGenerator.generateLongId();
  }

  public record AutoSignCommand(String businessKey, SignChannel signChannel) {}

  public record AutoSignResult(String batchNo, long batchId, boolean created) {}

  public record CreateOfflineBatchCommand(
      SignChannel signChannel,
      String businessKey,
      String expressCompany,
      String expressNo,
      String sender,
      Instant arriveTime,
      String operator) {}
}
