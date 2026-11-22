package com.bone.claim.application;

import com.bone.claim.application.ClaimCaseApplicationService.SubmitClaim;
import com.bone.claim.application.ClaimCaseApplicationService.SubmitClaim.ClaimItemDto;
import com.bone.claim.application.ClaimCaseApplicationService.SubmittedClaim;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimReport;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimReportRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 报案视角的应用服务：追加赔案、报案详情。
 *
 * <p><b>为什么不把这些方法塞进 {@code ClaimCaseApplicationService}</b>：那边的用例全部以赔案为主体（受理/初审/核赔/复核），
 * 这里的用例以「一次事故告知」为主体（这次事故还能不能再赔、这次事故一共赔了几笔）。两者的聚合根与不变量不同， 混在一个门面里会让「赔案状态机」与「报案可否追加」两套规则互相渗透。
 *
 * <p>分层纪律：本服务只做编排，不碰 SQL；赔案的实际建案复用 {@code
 * ClaimCaseApplicationService#appendCase}（同一条建案管线，避免追加案静默跳过码解析与 STP 闸门）。
 */
@Service
@RequiredArgsConstructor
public class ClaimReportApplicationService {

  private final ClaimReportRepository claimReportRepository;
  private final ClaimCaseRepository claimCaseRepository;
  private final ClaimCaseApplicationService claimCaseApplicationService;
  private final TenantPort tenantPort;

  /**
   * 追加赔案命令。
   *
   * <p><b>刻意不含保单维度字段</b>（保单号/计划/被保人/出险时间/计划版本）：追加赔付是同一事故的第二笔处理，
   * 这些值在事故维度恒定且已由报案持有。没有字段，就不存在「传了被忽略」的假象——调用方不会误以为覆盖成功。
   */
  public record AppendCaseCommand(
      String reportNo,
      String idempotencyKey,
      String claimType,
      String invoiceCode,
      String invoiceNo,
      LocalDate invoiceDate,
      String hospitalCode,
      String diagnosisCode,
      String batchNo,
      String consentRecordId,
      String insurerCode,
      List<ClaimItemDto> items) {

    /** 组装建案命令：事故上下文一律从报案取，不接受入参覆盖。 */
    SubmitClaim toSubmitClaim(ClaimReport report) {
      Instant outInsureTime = report.getOutInsureTime();
      return new SubmitClaim(
          idempotencyKey,
          // 保单号/保司号留空：SubmitClaim 的解析入口只在「首次提交」用；追加不重新解析保单，
          // 因此 buildClaim 也从报案取这些值，不读命令里的这两个字段。
          null,
          null,
          report.getPlanId(),
          report.getInsuredIdHash(),
          claimType,
          invoiceCode,
          invoiceNo,
          invoiceDate,
          hospitalCode,
          diagnosisCode,
          outInsureTime,
          batchNo,
          consentRecordId,
          insurerCode,
          items == null ? List.of() : items);
    }
  }

  /** 报案详情（报案头 + 名下赔案，按创建时间升序）。 */
  public record ReportDetail(ClaimReport report, List<ClaimCase> cases) {

    /** 是否全部赔案均已进入终态（派生口径，不落库：落库就会与赔案状态形成两份真源）。 */
    public boolean allCasesFinished() {
      return !cases.isEmpty() && cases.stream().allMatch(ClaimCase::finished);
    }
  }

  /**
   * 在指定报案下追加赔案。
   *
   * <p><b>继承而非覆盖</b>：保单号、计划、被保人、出险时间与计划版本全部取自报案。
   *
   * <p>报案已关闭 → 422（业务前提不成立）；报案不存在或跨租户 → 404（不区分，防枚举）。
   */
  @Transactional
  public SubmittedClaim appendCase(AppendCaseCommand command) {
    ClaimReport report = requireReport(command.reportNo());
    if (!report.claimable()) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_REPORT_CLOSED,
          report.getReportNo() + "（" + report.status().displayName() + "）");
    }
    return claimCaseApplicationService.appendCase(report, command.toSubmitClaim(report));
  }

  /** 报案详情：报案不存在与跨租户同号一律 404（防枚举），不返回空结构。 */
  @Transactional(readOnly = true)
  public ReportDetail detail(String reportNo) {
    ClaimReport report = requireReport(reportNo);
    List<ClaimCase> cases =
        claimCaseRepository.findByReportId(report.getTenantId(), report.getId());
    return new ReportDetail(report, cases);
  }

  private ClaimReport requireReport(String reportNo) {
    ClaimReport report =
        claimReportRepository.findByReportNo(tenantPort.currentTenantId(), reportNo);
    if (report == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_REPORT_NOT_FOUND, reportNo);
    }
    return report;
  }
}
