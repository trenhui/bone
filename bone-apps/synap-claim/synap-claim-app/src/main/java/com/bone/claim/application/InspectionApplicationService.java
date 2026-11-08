package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.inspection.InspectionAction;
import com.bone.claim.domain.model.inspection.InspectionDiffType;
import com.bone.claim.domain.model.inspection.InspectionDimension;
import com.bone.claim.domain.model.inspection.InspectionRecord;
import com.bone.claim.domain.model.inspection.InspectionResult;
import com.bone.claim.domain.model.inspection.InspectionSamplingPolicy;
import com.bone.claim.domain.model.inspection.InspectionStatus;
import com.bone.claim.domain.model.inspection.InspectionType;
import com.bone.claim.domain.repository.InspectionRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.util.DistributedIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A9 后督抽检应用服务（§7.6：抽样 → 抽检 → 结论 → 差异登记）。
 *
 * <p>每个写方法只持久化 {@code InspectionRecord} 一个聚合（CORE-06）。<b>不改案件状态</b>：差异回流（§6.1 质检态 30/31/32）属
 * V2，一期只在 {@code actionTaken}/{@code targetStatus} 登记处置意图——抽检结论直接改动已赔付案件，是一条 无签核的资金路径。
 *
 * <p><b>为什么抽检前置是「已终态」而不是「已支付」</b>：后督抽检同时要覆盖「AI 误判拒赔」（{@code CLOSED_REJECTED}） 与「多付/少付」（{@code
 * FINISHED}）两类风险，只看已支付会把最贵的漏赔风险排除在样本之外。代价是拒赔案件的金额复核无 结算单可比， 由抽检维度 {@code AMOUNT} 的明细自行记录依据。
 */
@Service
@RequiredArgsConstructor
public class InspectionApplicationService {

  private final InspectionRepository inspectionRepository;
  private final ClaimCaseApplicationService claimCaseApplicationService;
  private final TenantPort tenantPort;
  private final DomainEventPublisher domainEventPublisher;

  /** 抽检单视图（adapter 直接投影，不含领域对象）。 */
  public record InspectionView(
      String inspectionNo,
      Long claimId,
      String claimNo,
      String dimension,
      String sampleStratum,
      String inspectType,
      String status,
      String inspectorId,
      String result,
      String diffType,
      String diffDetail,
      String actionTaken,
      String targetStatus) {}

  /** 抽样结果（供调度汇总日志：命中/未命中/已存在，三者必须可区分）。 */
  public enum SampleOutcome {
    SAMPLED,
    NOT_SAMPLED,
    ALREADY_INSPECTED,
    NOT_ELIGIBLE
  }

  /**
   * 人工发起抽检（#13 MANUAL；幂等——同案已有抽检单则返回原单，不新建）。
   *
   * @param dimension 抽检维度（缺省 {@link InspectionDimension#AMOUNT}）
   */
  @Transactional
  public InspectionView openManually(
      long claimId, InspectionDimension dimension, String operatorId) {
    ClaimCase claim = requireInspectedClaim(claimId);
    InspectionRecord existing = inspectionRepository.findLatestByClaimId(claimId);
    if (existing != null) {
      return toView(existing);
    }
    InspectionRecord record = open(claim, dimension, InspectionType.MANUAL);
    inspectionRepository.insert(record);
    domainEventPublisher.publishFrom(record);
    return toView(record);
  }

  /**
   * 自动抽样（{@code InspectionSamplingJob} 逐租户驱动；须在租户上下文内调用）。
   *
   * <p>三重门槛：① 案件已终态；② 命中分层抽样比例；③ 该案件此前未被抽检。任一不满足即返回对应结果， <b>不静默吞掉</b>——否则 「抽样没生效」和「抽了但没建单」在日志里长得一样。
   *
   * @param ratePercent 抽样比例（百分比，0–100）
   */
  @Transactional
  public SampleOutcome sampleIfEligible(long claimId, int ratePercent) {
    ClaimCase claim = claimCaseApplicationService.getClaimAggregate(claimId);
    if (claim == null || !claim.finished()) {
      return SampleOutcome.NOT_ELIGIBLE;
    }
    Long tenantId = tenantPort.currentTenantId();
    if (!Long.valueOf(tenantId).equals(claim.getTenantId())) {
      return SampleOutcome.NOT_ELIGIBLE; // 全租户扫描行的租户与上下文不一致：拒绝跨租户建单
    }
    if (!InspectionSamplingPolicy.shouldSample(claimId, ratePercent)) {
      return SampleOutcome.NOT_SAMPLED;
    }
    if (inspectionRepository.findLatestByClaimId(claimId) != null) {
      return SampleOutcome.ALREADY_INSPECTED;
    }
    InspectionRecord record = open(claim, InspectionDimension.AMOUNT, InspectionType.AUTO);
    inspectionRepository.insert(record);
    domainEventPublisher.publishFrom(record);
    return SampleOutcome.SAMPLED;
  }

  /** 领取抽检任务（工作台：待抽检 → 抽检中）。 */
  @Transactional
  public InspectionView claim(String inspectionNo, String inspectorId) {
    InspectionRecord record = requireInspection(inspectionNo);
    record.claim(inspectorId);
    inspectionRepository.save(record);
    domainEventPublisher.publishFrom(record);
    return toView(record);
  }

  /**
   * 出抽检结论（#14：抽检中 → 已结论，终态）。
   *
   * <p>已结论再提交 → 409（不是 400）：这是「资源当前状态不允许该动作」的语义冲突，与状态机其他入口保持一致。
   */
  @Transactional
  public InspectionView conclude(
      String inspectionNo,
      InspectionResult result,
      InspectionDiffType diffType,
      String diffDetail,
      InspectionAction action,
      String targetStatus,
      String operatorId) {
    InspectionRecord record = requireInspection(inspectionNo);
    if (record.statusEnum().terminal()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_INSPECTION_CONFLICT, inspectionNo);
    }
    record.conclude(result, diffType, diffDetail, action, targetStatus, operatorId);
    inspectionRepository.save(record);
    domainEventPublisher.publishFrom(record);
    return toView(record);
  }

  /** 查询案件最新抽检单（无则 null）。 */
  @Transactional(readOnly = true)
  public InspectionView latest(long claimId) {
    InspectionRecord record = inspectionRepository.findLatestByClaimId(claimId);
    return record == null ? null : toView(record);
  }

  /** 按单号查询。 */
  @Transactional(readOnly = true)
  public InspectionView detail(String inspectionNo) {
    return toView(requireInspection(inspectionNo));
  }

  // ===================== 内部 =====================

  private InspectionRecord open(
      ClaimCase claim, InspectionDimension dimension, InspectionType type) {
    long claimId = claim.getId();
    long tenantId = claim.getTenantId();
    return InspectionRecord.open(
        DistributedIdGenerator.generateLongId(),
        tenantId,
        "QI" + tenantId + claimId, // 一期一案一检：单号确定性生成，重复发起天然被唯一键拦住
        claimId,
        claim.getClaimNo(),
        dimension == null ? InspectionDimension.AMOUNT : dimension,
        InspectionSamplingPolicy.sampleStratum(claim.getClaimAmount(), claim.getSourceChannel()),
        type);
  }

  /** 抽检前置：案件须已终态（FINISHED / CLOSED_REJECTED），否则 409。 */
  private ClaimCase requireInspectedClaim(long claimId) {
    ClaimCase claim = claimCaseApplicationService.getClaimAggregate(claimId);
    if (claim == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_NOT_FOUND, claimId);
    }
    if (!claim.finished()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_STATUS_CONFLICT, "案件未终态不可后督抽检: " + claimId);
    }
    return claim;
  }

  private InspectionRecord requireInspection(String inspectionNo) {
    InspectionRecord record =
        inspectionNo == null ? null : inspectionRepository.findByInspectionNo(inspectionNo);
    if (record == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_INSPECTION_NOT_FOUND, inspectionNo);
    }
    return record;
  }

  private static InspectionView toView(InspectionRecord record) {
    return new InspectionView(
        record.getInspectionNo(),
        record.getClaimId(),
        record.getClaimNo(),
        record.getDimension(),
        record.getSampleStratum(),
        record.getInspectType(),
        record.getStatus(),
        record.getInspectorId(),
        record.getResult(),
        record.getDiffType(),
        record.getDiffDetail(),
        record.getActionTaken(),
        record.getTargetStatus());
  }

  /** 状态枚举判空便捷方法（供 adapter 组装展示用）。 */
  public static boolean isConcluded(InspectionView view) {
    return view != null && InspectionStatus.CONCLUDED.name().equals(view.status());
  }
}
