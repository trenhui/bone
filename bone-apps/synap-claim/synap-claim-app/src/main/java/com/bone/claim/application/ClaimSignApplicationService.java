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
import java.math.BigDecimal;
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
    if (command.caseElements() != null) {
      for (CaseElement element : command.caseElements()) {
        batch.addItem(
            SignBatchItem.withCaseElements(
                tenantId,
                batch.getId(),
                element.claimNo(),
                itemBusinessKey(command.businessKey(), element.claimNo()),
                element.policyNo(),
                element.planId(),
                element.insuredName(),
                element.insuredIdNo(),
                element.outInsureTime(),
                element.claimType(),
                element.sourceChannel(),
                amountOf(element.claimAmount(), command.claimAmount())));
      }
    }
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
    batch.withSigningParties(
        command.insuranceCompany(),
        command.insuranceSubsidiary(),
        command.insuringAgency(),
        command.emergency());
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
  public PageResult<ClaimSignBatch> list(SignBatchQuery query) {
    return batchRepository.findBatchPage(
        tenantPort.currentTenantId(),
        query.source(),
        query.batchNo(),
        query.signType(),
        query.signChannel(),
        query.signStatus(),
        query.insuranceCompany(),
        query.insuranceSubsidiary(),
        query.insuringAgency(),
        query.signOperator(),
        query.signTimeStart(),
        query.signTimeEnd(),
        query.createTimeStart(),
        query.createTimeEnd(),
        query.page(),
        query.size());
  }

  /** 批次内赔案明细，按赔案号稳定排序，供详情页只读展示。 */
  @Transactional(readOnly = true)
  public List<SignBatchItem> items(long batchId) {
    requireBatch(tenantPort.currentTenantId(), batchId);
    return batchRepository.findItemsByBatchId(batchId).stream()
        .sorted(
            java.util.Comparator.comparing(
                SignBatchItem::getClaimNo,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
        .toList();
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

  @Transactional(readOnly = true)
  public ClaimSignCaseSummary caseSummary(long batchId) {
    requireBatch(tenantPort.currentTenantId(), batchId);
    int created = 0;
    int skipped = 0;
    int manual = 0;
    java.util.List<ClaimSignCaseSummary.Item> items = new java.util.ArrayList<>();
    for (SignBatchItem item : batchRepository.findItemsByBatchId(batchId)) {
      String status = item.getCaseStatus();
      if (SignBatchItem.CASE_NEW.equals(status)) {
        created++;
      } else if (SignBatchItem.CASE_SKIPPED.equals(status)) {
        skipped++;
      } else {
        manual++;
      }
      items.add(
          new ClaimSignCaseSummary.Item(
              item.getId(),
              item.getClaimNo(),
              item.getCreatedCaseId() == null ? item.getClaimId() : item.getCreatedCaseId(),
              status,
              caseResultText(status)));
    }
    return new ClaimSignCaseSummary(created, skipped, manual, items);
  }

  private static String caseResultText(String status) {
    return switch (status == null ? "" : status) {
      case SignBatchItem.CASE_NEW -> "已建案并自动流转";
      case SignBatchItem.CASE_SKIPPED -> "引用或已存在案件，跳过建案";
      default -> "待人工补录或处理";
    };
  }

  /** 明细幂等键 ≤128 列宽：超长走前缀 + FNV-1a，避免真库静默截断。 */
  private static String itemBusinessKey(String businessKey, String claimNo) {
    String raw = businessKey + ":" + (claimNo == null ? "" : claimNo);
    if (raw.length() <= 128) {
      return raw;
    }
    int hash = 0x811c9dc5;
    for (byte b : raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
      hash ^= b & 0xff;
      hash *= 0x01000193;
    }
    return raw.substring(0, 110) + "-" + Integer.toHexString(hash);
  }

  private static String generateBatchNo() {
    return "SG" + DistributedIdGenerator.generateLongId();
  }

  public record AutoSignCommand(
      String businessKey,
      SignChannel signChannel,
      List<CaseElement> caseElements,
      Long claimAmount) {

    public AutoSignCommand(String businessKey, SignChannel signChannel) {
      this(businessKey, signChannel, List.of(), null);
    }

    public AutoSignCommand(
        String businessKey, SignChannel signChannel, List<CaseElement> caseElements) {
      this(businessKey, signChannel, caseElements, null);
    }
  }

  /** 明细金额缺省时回落顶层金额；两者都缺才是真正的未知（案件金额保持 0，转人工判断）。 */
  private static BigDecimal amountOf(Long itemAmount, Long batchAmount) {
    Long amount = itemAmount == null ? batchAmount : itemAmount;
    return amount == null ? null : BigDecimal.valueOf(amount);
  }

  public record AutoSignResult(String batchNo, long batchId, boolean created) {}

  /** 外部推送随回执携带的建案要素（缺项时签名明细落到待人工，不拒签收）。 */
  public record CaseElement(
      String claimNo,
      String policyNo,
      Long planId,
      String insuredName,
      String insuredIdNo,
      Instant outInsureTime,
      String claimType,
      String sourceChannel,
      Long claimAmount) {

    public CaseElement(
        String claimNo,
        String policyNo,
        Long planId,
        String insuredName,
        String insuredIdNo,
        Instant outInsureTime,
        String claimType,
        String sourceChannel) {
      this(
          claimNo,
          policyNo,
          planId,
          insuredName,
          insuredIdNo,
          outInsureTime,
          claimType,
          sourceChannel,
          null);
    }
  }

  public record CreateOfflineBatchCommand(
      SignChannel signChannel,
      String businessKey,
      String expressCompany,
      String expressNo,
      String sender,
      Instant arriveTime,
      String operator,
      String insuranceCompany,
      String insuranceSubsidiary,
      String insuringAgency,
      String emergency) {}

  /** 服务端组合查询入参（应用层自有形状，不依赖 adapter DTO）。 */
  public record SignBatchQuery(
      String source,
      String batchNo,
      String signType,
      String signChannel,
      String signStatus,
      String insuranceCompany,
      String insuranceSubsidiary,
      String insuringAgency,
      String signOperator,
      Instant signTimeStart,
      Instant signTimeEnd,
      Instant createTimeStart,
      Instant createTimeEnd,
      int page,
      int size) {}
}
