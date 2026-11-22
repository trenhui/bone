package com.bone.claim.domain.model.claim;

import com.bone.claim.domain.model.claim.valueobject.ReportStatus;
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
 * 报案聚合根（FNOL：一次事故告知事件）。
 *
 * <p><b>为什么报案要独立成聚合</b>：报案是「客户告知发生了什么事故」，赔案是「我们为这件事办了一笔赔付」。一次告知可以产生 0
 * 到多笔赔付（多张发票、二次手术追加、分责任理算），两者是事件与处理单元的关系，天然不是一一对应。把它们压成一个实体， 「报案了但还没立案」这种合法状态就无处表达，只能靠删行或标脏数据。
 *
 * <p><b>基数</b>：{@code 报案 : 赔案 = 1:0..N}。本聚合<b>不持有</b>赔案集合——赔案有自己的状态机与生命周期，
 * 做成级联子集合会让「保存报案」变成「批量改写赔案状态」。反方向由 {@code ClaimCase.reportId} 单向引用， 「某报案下的赔案」走 {@code
 * ClaimCaseRepository.findByReportId}。
 *
 * <p><b>状态只表达可否追加</b>：见 {@link ReportStatus}。赔付进度由赔案派生，本聚合不落。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_report")
public class ClaimReport extends TenantAggregateRoot<Long> {

  /** 报案号（对外检索键，与物理 id 分离；{@code RP + yyyyMMdd + Base32(Feistel(id)) + 校验位}，全局唯一）。 */
  @Column(name = "report_no", nullable = false)
  private String reportNo;

  /**
   * 保单号（<b>平台保单号</b>，不是保司侧号）。
   *
   * <p>报案持有事故的保单上下文，赔案一律从它继承；这样「同一事故的赔付落在同一保单」由结构保证， 而不是靠每个调用方自觉传对参数。
   */
  @Column(name = "policy_no", nullable = false)
  private String policyNo;

  @Column(name = "plan_id", nullable = false)
  private Long planId;

  /** 假名化被保人（服务端 HMAC 计算，明文不落库）。 */
  @Column(name = "insured_id_hash", nullable = false)
  private String insuredIdHash;

  @Column(name = "claim_type", nullable = false)
  private String claimType;

  /** 出险（事故）时间：事故的事实属性，赔案继承，不接受各赔案各写一份。 */
  @Column(name = "out_insure_time", nullable = false)
  private Instant outInsureTime;

  /**
   * 受理时锁定的计划版本（历史案件复算确定性，§4.1）。
   *
   * <p><b>为什么落在报案上</b>：计划版本在「受理」这一刻锁定，而受理是报案的动作。赔案若各存一份， 同一事故的多个赔案可能锁到不同版本 ——
   * 复算时算不平，且没有任何一处能回答「这次事故按哪版计划赔的」。
   */
  @Column(name = "plan_version")
  private String planVersion;

  @Column(name = "source_channel")
  private String sourceChannel = "";

  @Column(name = "source_system", nullable = false)
  private String sourceSystem;

  /** 报案状态（DB 存枚举 name：{@code REPORTED} / {@code CLOSED}）。 */
  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** 乐观锁版本号（SDK 原生 @Version，ADR-0031）：领域层不手写版本递增。 */
  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  /**
   * 登记报案（受理前）。
   *
   * <p>报案号由 id 派生，客户端不可指定：报案号是对账与客服检索的锚点，允许外部指定等于允许伪造/重号。
   *
   * @param id 报案物理主键（调用方预分配雪花 id）
   * @param tenantId 租户（与名下赔案必须一致）
   * @param policyNo 平台保单号（调用方已解析，不得传保司侧号）
   * @param planId 计划 id
   * @param insuredIdHash 假名化被保人
   * @param claimType 险种（空则按门诊快赔兜底，与赔案 draft 同口径）
   * @param outInsureTime 出险时间（必填；事故时间是继承源，不允许缺省）
   * @param sourceChannel 渠道（可空）
   */
  public static ClaimReport open(
      long id,
      Long tenantId,
      String policyNo,
      Long planId,
      String insuredIdHash,
      String claimType,
      Instant outInsureTime,
      String planVersion,
      String sourceChannel) {
    if (policyNo == null || policyNo.isBlank()) {
      throw new DomainException("报案必须带保单号（平台保单号），否则赔案无法继承事故上下文");
    }
    if (planId == null) {
      throw new DomainException("报案必须带计划ID（赔案从报案继承计划，不接受各案各写一份）");
    }
    if (insuredIdHash == null || insuredIdHash.isBlank()) {
      throw new DomainException("被保人假名化ID不能为空（服务端计算，客户端不传明文）");
    }
    if (outInsureTime == null) {
      throw new DomainException("出险时间不能为空（它是赔案继承的事故事实，不允许缺省）");
    }
    ClaimReport report = new ClaimReport();
    report.setId(id);
    report.setTenantId(tenantId);
    report.reportNo = ReportNoGenerator.generate(id);
    report.policyNo = policyNo;
    report.planId = planId;
    report.insuredIdHash = insuredIdHash;
    report.claimType = claimType == null || claimType.isBlank() ? "OUTPATIENT" : claimType;
    report.outInsureTime = outInsureTime;
    report.planVersion = planVersion == null ? "" : planVersion;
    report.sourceChannel = sourceChannel == null ? "" : sourceChannel.trim();
    report.sourceSystem = "self";
    report.status = ReportStatus.REPORTED.name();
    Instant now = Instant.now();
    report.createdAt = now;
    report.updatedAt = now;
    report.version = 0L;
    return report;
  }

  /**
   * 关闭报案（不再受理新赔案）。
   *
   * <p><b>只拦「追加」</b>：已立案的赔案继续走自己的状态机——报案关闭的语义是「不再受理新赔付」，不是「把在办案件卡死」。
   *
   * <p>幂等：已关闭返回 {@code false}（重复关闭是重试常态，不是错误）。
   *
   * @return 是否发生变更
   */
  public boolean close() {
    if (status() == ReportStatus.CLOSED) {
      return false;
    }
    this.status = ReportStatus.CLOSED.name();
    this.updatedAt = Instant.now();
    return true;
  }

  /** 是否还能追加赔案（意图揭示查询：状态解释权归聚合，应用层不得直接比较字段）。 */
  public boolean claimable() {
    return status() == ReportStatus.REPORTED;
  }

  /** 状态枚举（DB name → 枚举；null 视为 REPORTED，兼容历史行）。 */
  public ReportStatus status() {
    return status == null ? ReportStatus.REPORTED : ReportStatus.ofName(status);
  }
}
