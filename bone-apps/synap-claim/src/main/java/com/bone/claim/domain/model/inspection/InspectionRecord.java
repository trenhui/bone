package com.bone.claim.domain.model.inspection;

import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.inspection.event.InspectionConcludedEvent;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 后督抽检聚合（§7.6 / §9.5，多租户）。
 *
 * <p><b>状态机</b>：{@code PENDING}（待抽检）→ {@code IN_PROGRESS}（抽检中）→ {@code CONCLUDED}（已结论，终态）。
 * 结论不可改写——后督结论是审计证据，需更正时另开一次抽检，而不是覆盖原结论。
 *
 * <p><b>结论自洽不变量</b>（本类存在的核心理由，防「结论 PASS 却带差异」「结论 DIFF 却无归因」这类脏数据）：
 *
 * <ul>
 *   <li>{@code PASS} ⇒ {@code diffType == NONE}、{@code action == NONE}、{@code targetStatus} 空、无
 *       {@code diffDetail}；
 *   <li>{@code DIFF} ⇒ {@code diffType != NONE} 且 {@code action != NONE} 且 {@code diffDetail} 非空。
 * </ul>
 *
 * <p><b>处置只登记不执行</b>：{@code actionTaken}/{@code targetStatus} 记录质检员的处置意图，案件状态回流（§6.1 质检态 30/31/32）属
 * V2。一期若自动改终态案件，等于「抽检即改动已赔付案件」的无签核资金动作。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_inspection")
public class InspectionRecord extends TenantAggregateRoot<Long> {

  /** 质检段保留状态码（§6.1 30/31/32 INSPECTION）：一期未建模，但允许作为处置目标登记，避免质检员意图无法留痕。 */
  private static final Set<String> RESERVED_TARGET_CODES = Set.of("30", "31", "32");

  /** 差异明细长度上限（JSON 列实际可用容量内的保守值）。 */
  private static final int MAX_DIFF_DETAIL_LENGTH = 1000;

  @Column(name = "inspection_no", nullable = false)
  private String inspectionNo;

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "claim_no", nullable = false)
  private String claimNo = "";

  @Column(name = "dimension", nullable = false)
  private String dimension;

  @Column(name = "sample_stratum", nullable = false)
  private String sampleStratum = "";

  @Column(name = "inspect_type", nullable = false)
  private String inspectType;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "inspector_id", nullable = false)
  private String inspectorId = "";

  @Column(name = "inspected_at")
  private Instant inspectedAt;

  @Column(name = "result", nullable = false)
  private String result = "";

  @Column(name = "diff_type", nullable = false)
  private String diffType = InspectionDiffType.NONE.name();

  @Column(name = "diff_detail")
  private String diffDetail;

  @Column(name = "action_taken", nullable = false)
  private String actionTaken = InspectionAction.NONE.name();

  @Column(name = "target_status", nullable = false)
  private String targetStatus = "";

  @Version
  @Column(name = "version")
  private Long version;

  private InspectionRecord(
      Long id,
      Long tenantId,
      String inspectionNo,
      Long claimId,
      String claimNo,
      InspectionDimension dimension,
      String sampleStratum,
      InspectionType inspectType) {
    setId(id);
    setTenantId(tenantId);
    this.inspectionNo = inspectionNo;
    this.claimId = claimId;
    this.claimNo = claimNo == null ? "" : claimNo;
    this.dimension = dimension.name();
    this.sampleStratum = sampleStratum == null ? "" : sampleStratum;
    this.inspectType = inspectType.name();
    this.status = InspectionStatus.PENDING.name();
    this.version = 0L;
  }

  /**
   * 发起抽检（待抽检）。
   *
   * @param inspectionNo 抽检单号（租户内唯一，客户端不可自定义）
   * @param sampleStratum 分层抽样标识（{@link InspectionSamplingPolicy#sampleStratum}）
   */
  public static InspectionRecord open(
      Long id,
      Long tenantId,
      String inspectionNo,
      Long claimId,
      String claimNo,
      InspectionDimension dimension,
      String sampleStratum,
      InspectionType inspectType) {
    if (inspectionNo == null || inspectionNo.isBlank()) {
      throw new DomainException("抽检单号不能为空");
    }
    if (claimId == null) {
      throw new DomainException("抽检必须关联案件");
    }
    if (dimension == null) {
      throw new DomainException("抽检维度不能为空（AMOUNT/DOCUMENT/MEDICAL/PROCESS）");
    }
    if (inspectType == null) {
      throw new DomainException("抽检发起方式不能为空（AUTO/MANUAL）");
    }
    return new InspectionRecord(
        id, tenantId, inspectionNo, claimId, claimNo, dimension, sampleStratum, inspectType);
  }

  /** 领取抽检任务（PENDING → IN_PROGRESS；同一质检人重复领取幂等）。 */
  public void claim(String inspectorId) {
    requireNotConcluded();
    if (inspectorId == null || inspectorId.isBlank()) {
      throw new DomainException("质检人不能为空");
    }
    if (statusEnum() == InspectionStatus.IN_PROGRESS) {
      return; // 幂等：已由同一质检人领取
    }
    this.inspectorId = inspectorId;
    this.status = InspectionStatus.IN_PROGRESS.name();
  }

  /**
   * 出结论（PENDING/IN_PROGRESS → CONCLUDED）。
   *
   * <p>允许从 {@code PENDING} 直接结论（质检员一次性回填，免「先领取再出结论」两步）；结论与处置必须自洽，见类注释。
   *
   * @param result 结论（PASS/DIFF）
   * @param diffType 差异类型（PASS 时须 NONE）
   * @param diffDetail 差异明细 JSON（DIFF 时必填）
   * @param action 处置意图（PASS 时须 NONE）
   * @param targetStatus 处置目标状态（可空；非空须为受控状态码）
   */
  public void conclude(
      InspectionResult result,
      InspectionDiffType diffType,
      String diffDetail,
      InspectionAction action,
      String targetStatus,
      String inspectorId) {
    requireNotConcluded();
    if (result == null) {
      throw new DomainException("抽检结论不能为空（PASS/DIFF）");
    }
    if (inspectorId == null || inspectorId.isBlank()) {
      throw new DomainException("结论人不能为空");
    }
    InspectionDiffType effectiveDiff = diffType == null ? InspectionDiffType.NONE : diffType;
    InspectionAction effectiveAction = action == null ? InspectionAction.NONE : action;
    String normalizedTarget = targetStatus == null ? "" : targetStatus.trim();
    boolean hasDiffDetail = diffDetail != null && !diffDetail.isBlank();

    if (result == InspectionResult.PASS) {
      if (effectiveDiff != InspectionDiffType.NONE
          || effectiveAction != InspectionAction.NONE
          || !normalizedTarget.isEmpty()
          || hasDiffDetail) {
        throw new DomainException("结论 PASS 时不得携带差异类型/处置动作/目标状态/差异明细（结论须自洽）");
      }
    } else {
      if (effectiveDiff == InspectionDiffType.NONE) {
        throw new DomainException(
            "结论 DIFF 必须给出受控差异类型（ADJUST_MISMATCH/RULE_MISS/AI_MISJUDGE/QUOTA_ABNORMAL）");
      }
      if (effectiveAction == InspectionAction.NONE) {
        throw new DomainException("结论 DIFF 必须给出处置动作（RECALCULATE/REVIEW/REFUND/CLOSE）");
      }
      if (!hasDiffDetail) {
        throw new DomainException("结论 DIFF 必须给出差异明细（防「有差异但说不出差在哪」）");
      }
    }
    requireControlledTargetStatus(normalizedTarget);
    requireBoundedDetail(diffDetail);

    this.status = InspectionStatus.CONCLUDED.name();
    this.inspectorId = inspectorId;
    this.inspectedAt = Instant.now();
    this.result = result.name();
    this.diffType = effectiveDiff.name();
    this.diffDetail = hasDiffDetail ? diffDetail.trim() : null;
    this.actionTaken = effectiveAction.name();
    this.targetStatus = normalizedTarget;

    addDomainEvent(
        new InspectionConcludedEvent(
            getId(),
            getTenantId(),
            inspectionNo,
            claimId,
            result,
            effectiveDiff,
            effectiveAction,
            normalizedTarget,
            inspectorId,
            inspectedAt));
  }

  public InspectionStatus statusEnum() {
    return InspectionStatus.valueOf(status);
  }

  public InspectionResult resultEnum() {
    return result == null || result.isBlank() ? null : InspectionResult.valueOf(result);
  }

  public InspectionDiffType diffTypeEnum() {
    return InspectionDiffType.valueOf(diffType);
  }

  public InspectionAction actionEnum() {
    return InspectionAction.valueOf(actionTaken);
  }

  public InspectionDimension dimensionEnum() {
    return InspectionDimension.valueOf(dimension);
  }

  private void requireNotConcluded() {
    if (statusEnum().terminal()) {
      throw new DomainException("抽检已结论（" + inspectionNo + "），结论不可改写；需更正请另开一次抽检");
    }
  }

  /** 目标状态兜底：空放行；否则须为已建模状态枚举名或质检段保留码（30/31/32）。 */
  private static void requireControlledTargetStatus(String targetStatus) {
    if (targetStatus.isEmpty()) {
      return;
    }
    if (targetStatus.length() > 32) {
      throw new DomainException("处置目标状态超长（≤32）: " + targetStatus.length());
    }
    if (RESERVED_TARGET_CODES.contains(targetStatus)) {
      return; // §6.1 质检段码：一期只登记，回流执行属 V2
    }
    try {
      ClaimStatus.ofName(targetStatus);
    } catch (IllegalArgumentException ex) {
      throw new DomainException("处置目标状态非法（须为 §6.2 状态枚举名或质检段 30/31/32），实际: " + targetStatus);
    }
  }

  /** 差异明细长度上限：超长明细写不进 JSON 列、且会让审计读取变慢（明细只须「哪个字段、本方值、复核值」）。 */
  private static void requireBoundedDetail(String diffDetail) {
    if (diffDetail != null && diffDetail.length() > MAX_DIFF_DETAIL_LENGTH) {
      throw new DomainException(
          "差异明细超长（≤" + MAX_DIFF_DETAIL_LENGTH + " 字符），实际: " + diffDetail.length());
    }
  }
}
