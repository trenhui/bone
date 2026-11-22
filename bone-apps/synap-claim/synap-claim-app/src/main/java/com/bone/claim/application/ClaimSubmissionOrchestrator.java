package com.bone.claim.application;

import com.bone.claim.application.ClaimCaseApplicationService.SubmitClaim;
import com.bone.claim.application.ClaimReportApplicationService.RegisterReport;
import com.bone.claim.application.ClaimReportApplicationService.RegisteredCase;
import com.bone.claim.application.ClaimReportApplicationService.RegisteredReport;
import com.bone.claim.application.ClaimReportApplicationService.SplitCase;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 报案 + 赔案跨聚合编排（1:N 主链路）。
 *
 * <p><b>为什么单独一层</b>：报案（ClaimReport）与赔案（ClaimCase）是两个聚合、两个一致性边界，R9 要求一个写事务只持久化一个聚合。
 * 本编排层只串联已有应用服务，<b>自身不注入任何 Repository、不直接持久化</b>。
 *
 * <p><b>关键约束：本类所有方法禁止加 {@code @Transactional}</b>。若加了外层事务，内层两个 ApplicationService 的
 * {@code @Transactional} 默认 REQUIRED 会加入同一物理事务，把两个聚合又塞回一个事务——既绕过 R9 门禁的方法级盲区，
 * 又违背「已报案未立案」这一合法中间态。两段必须各自开启独立事务。
 *
 * <p><b>一致性取舍</b>：先报案后建案，建案失败遗留的报案无需补偿删除（FNOL 本就可独立存在）； 分案时先建子案再记报案计数，计数失败仅产生可对账的偏差，不回滚已成立的赔案。
 */
@Service
@RequiredArgsConstructor
public class ClaimSubmissionOrchestrator {

  private final ClaimReportApplicationService claimReportApplicationService;
  private final ClaimCaseApplicationService claimCaseApplicationService;

  /**
   * 用户提交理赔主链路：先登记报案（独立事务），再在报案下建首赔案（独立事务）。
   *
   * <p>幂等：两段各自按幂等键去重，重复提交返回首次报案与首次赔案。报案侧 policyNo 记录渠道原始入参（平台号优先、
   * 无平台号时退保司号），赔案侧落解析后的平台号——报案是事故陈述，赔案是核定归属，二者语义不同。
   */
  public ClaimCaseApplicationService.SubmittedClaim submit(SubmitClaim command) {
    // 与 ClaimCaseApplicationService#resolvePolicy 同判据：此处提前 fail-fast 是为了让报案阶段就抛出正确错误码，
    // 否则空号会先撞 ClaimReport.register 的「报案保单号不能为空」域异常（400 + 通用码），对外语义漂移。
    if ((command.policyNo() == null || command.policyNo().isBlank())
        && (command.insurerPolicyNo() == null || command.insurerPolicyNo().isBlank())) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_POLICY_KEY_REQUIRED);
    }
    String reportedPolicyNo =
        command.policyNo() != null && !command.policyNo().isBlank()
            ? command.policyNo()
            : command.insurerPolicyNo();
    RegisteredReport report =
        claimReportApplicationService.registerFromSubmission(
            new RegisterReport(
                command.idempotencyKey(),
                reportedPolicyNo,
                command.insuredIdHash(),
                command.outInsureTime(),
                null, // 员工端提交链路不携带事故描述，报案实体该字段留空
                null, // 来源渠道由上层渠道 API 显式登记；员工端提交不声明
                "synap-claim"));
    return claimCaseApplicationService.submit(command, report.reportId(), report.reportNo());
  }

  /** 分案：先建子赔案（独立事务），再以独立事务登记报案计数。 */
  public RegisteredCase splitCase(SplitCase command, String idempotencyKey) {
    RegisteredCase child = claimReportApplicationService.splitCase(command, idempotencyKey);
    claimReportApplicationService.recordCaseCreation(command.reportNo());
    return child;
  }
}
