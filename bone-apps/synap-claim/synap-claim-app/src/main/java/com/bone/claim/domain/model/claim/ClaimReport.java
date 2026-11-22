package com.bone.claim.domain.model.claim;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 报案聚合根（FNOL，First Notice of Loss）——一次事故告知事件，1:N 关系中的"1"。
 *
 * <p><b>报案 vs 赔案</b>（业界最佳实践，JR/T 0212-2020 把报案/立案/赔案列为独立实体）：
 *
 * <ul>
 *   <li>报案 {@link ClaimReport} = 客户告知"发生了什么事"（事件），一个报案号 {@code report_no}；
 *   <li>赔案 {@link ClaimCase} = 我们为这件事办的"一笔赔付"（处理单元），每笔一个 {@code claim_no}，通过 {@code report_id}
 *       归属报案。
 * </ul>
 *
 * <p>一个报案下可有 N 个赔案：一次事故多张发票/多个责任（人伤与车损分案）、后续追加索赔（二次手术）等场景各自独立理算结案。 报案号与赔案号是两套独立编号（{@link
 * ReportNoGenerator} / {@link ClaimNoGenerator}），前缀不同、号码空间不冲突。
 *
 * <p>报案状态极简：{@code REPORTED}（已登记，等待/正在建赔案）→ {@code CLOSED}（该报案下不再建新赔案）。报案本身不参与资金
 * 与理算——额度、资金幂等键、结案都以赔案号为单位，故 1:N 拆分不会踩踏资金键（见 §9.3.1）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_report")
public class ClaimReport extends TenantAggregateRoot<Long> {

  /** 报案状态：已登记（默认，可继续分案）/ 已关闭（不再建新赔案）。 */
  public static final String STATUS_REPORTED = "REPORTED";

  public static final String STATUS_CLOSED = "CLOSED";

  /** 报案号（对外展示/FNOL 检索键，RP 前缀，全局唯一；由 id 派生，可逆推报案主键）。 */
  @Column(name = "report_no", nullable = false)
  private String reportNo;

  /** 写接口幂等键（报案登记 Idempotency-Key），同租户内唯一。 */
  @Column(name = "idempotency_key", nullable = false)
  private String idempotencyKey;

  /** 平台保单号（报案归属保单；分案时子案继承）。 */
  @Column(name = "policy_no", nullable = false)
  private String policyNo;

  /** 假名化被保人 ID。 */
  @Column(name = "insured_id_hash", nullable = false)
  private String insuredIdHash;

  /** 出险时间（事故发生时刻，报案的核心事实）。 */
  @Column(name = "out_insure_time", nullable = false)
  private Instant outInsureTime;

  /** 事故/出险简述（人工登记的事实在哪儿，PII 不落明文）。 */
  @Column(name = "loss_description")
  private String lossDescription;

  /** 渠道 / 来源系统。 */
  @Column(name = "source_channel")
  private String sourceChannel = "";

  @Column(name = "source_system", nullable = false)
  private String sourceSystem = "self";

  /** 报案状态（REPORTED / CLOSED）。 */
  @Column(name = "status", nullable = false)
  private String status = STATUS_REPORTED;

  /** 已建赔案数（分案时递增；与 t_claim_case.report_id 统计对账用，非独立真源）。 */
  @Column(name = "case_count", nullable = false)
  private int caseCount = 0;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** 乐观锁版本号（SDK 维护；分案并发递增 case_count 用）。 */
  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  /** 登记报案（FNOL 起点）。id 由调用方预分配（雪花），报案号由 id 派生，全局唯一且可重复推导。 */
  public static ClaimReport register(
      long id,
      Long tenantId,
      String idempotencyKey,
      String policyNo,
      String insuredIdHash,
      Instant outInsureTime,
      String lossDescription,
      String sourceChannel,
      String sourceSystem) {
    if (policyNo == null || policyNo.isBlank()) {
      throw new DomainException("报案保单号不能为空");
    }
    if (insuredIdHash == null || insuredIdHash.isBlank()) {
      throw new DomainException("报案被保人假名化ID不能为空");
    }
    if (outInsureTime == null) {
      throw new DomainException("报案出险时间不能为空");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new DomainException("报案必须携带 Idempotency-Key（客户端生成），服务端不代生成");
    }
    ClaimReport report = new ClaimReport();
    report.setId(id);
    report.setTenantId(tenantId);
    report.reportNo = ReportNoGenerator.generate(id);
    report.idempotencyKey = idempotencyKey;
    report.policyNo = policyNo;
    report.insuredIdHash = insuredIdHash;
    report.outInsureTime = outInsureTime;
    report.lossDescription =
        lossDescription == null || lossDescription.isBlank() ? null : lossDescription.trim();
    report.sourceChannel = sourceChannel == null ? "" : sourceChannel.trim();
    report.sourceSystem =
        sourceSystem == null || sourceSystem.isBlank() ? "self" : sourceSystem.trim();
    report.status = STATUS_REPORTED;
    report.caseCount = 0;
    report.version = 0L;
    Instant now = Instant.now();
    report.createdAt = now;
    report.updatedAt = now;
    return report;
  }

  /** 报案号生成：委托 {@link ReportNoGenerator}（RP 前缀，24 字符）。 */
  public static String generateReportNo(long id) {
    return ReportNoGenerator.generate(id);
  }

  /** 分案后登记一个赔案：递增计数（仅允许 REPORTED 态，CLOSED 不再建新案）。 */
  public void onCaseCreated() {
    if (!STATUS_REPORTED.equals(this.status)) {
      throw new DomainException("报案已关闭，不能继续建赔案：" + reportNo);
    }
    this.caseCount++;
    this.updatedAt = Instant.now();
  }

  /** 关闭报案（该事故不再分案）。幂等：已关闭返回 false。 */
  public boolean close() {
    if (STATUS_CLOSED.equals(this.status)) {
      return false;
    }
    this.status = STATUS_CLOSED;
    this.updatedAt = Instant.now();
    return true;
  }

  /** 是否可继续分案。 */
  public boolean canSplit() {
    return STATUS_REPORTED.equals(this.status);
  }
}
