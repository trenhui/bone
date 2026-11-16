package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.inspection.ClaimInspectionAction;
import com.bone.claim.domain.model.settlement.SettlementOrder;
import com.bone.claim.domain.model.task.TaskType;
import com.bone.claim.domain.repository.ClaimInspectionActionRepository;
import com.bone.claim.domain.repository.SettlementRepository;
import com.bone.core.util.DistributedIdGenerator;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 抽检处置应用服务（批 4 任务 2.1 / 2.2 / 4.3）。
 *
 * <p>把后督抽检从「只登记处置意图」升级为「可处置、可追溯、可回退」，但守住两条资金与内控红线：
 *
 * <ul>
 *   <li><b>冲正前置（2.2）</b>：已支付案件打回前必须先冲正。资金路径无签核，未冲正就打回会留下「账上已付、案件已打回重办」的缺口。 判定只认资金事实（结算单当前阶段
 *       PAID），<b>不接受</b>客户端声明「已冲正」；写入期硬拒，不事后补偿。
 *   <li><b>只登记模式（4.3）</b>：{@code INSPECTION_ACTION} 关闭时，处置只落留痕（{@code featureEnabled=0}），
 *       不生成重办任务、不改案件状态。开关开启时才生成重办任务并回填关联 taskId。
 * </ul>
 *
 * <p>处置留痕是 append-only 事实：一旦落库不可改写；需更正只能再追加一条，读侧取最新。
 */
@Service
@RequiredArgsConstructor
public class InspectionDispositionService {

  private final ClaimInspectionActionRepository actionRepository;
  private final SettlementRepository settlementRepository;
  private final ClaimTaskApplicationService claimTaskApplicationService;
  private final FeatureTenantToggleService featureTenantToggleService;
  private final TenantPort tenantPort;

  /** 处置结果视图（雪花 ID 一律 String，前端 JS 精度安全）。 */
  public record DispositionView(
      String actionId,
      String claimId,
      String decision,
      String taskId,
      boolean reversalVerified,
      boolean featureEnabled) {}

  /**
   * 登记抽检处置。
   *
   * @param claimId 案件 ID
   * @param decision PASS/REJECT/BACK（三值，其余拒绝）
   * @param backReason 打回原因（BACK 时必填）
   * @param inspectorId 抽检人（取认证主体，不接受客户端声明）
   */
  @Transactional
  public DispositionView dispose(
      long claimId, String decision, String backReason, String inspectorId) {
    String normalized = decision == null ? "" : decision.trim().toUpperCase();
    boolean isBack = ClaimInspectionAction.DECISION_BACK.equals(normalized);
    if (!ClaimInspectionAction.DECISION_PASS.equals(normalized)
        && !ClaimInspectionAction.DECISION_REJECT.equals(normalized)
        && !isBack) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_INSPECTION_ACTION_INVALID, decision);
    }
    if (isBack && (backReason == null || backReason.isBlank())) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_INSPECTION_ACTION_INVALID, "打回原因必填");
    }

    long tenantId = tenantPort.currentTenantId();
    boolean featureEnabled = featureTenantToggleService.isInspectionActionEnabled(tenantId, true);

    // 冲正前置（2.2）：只在打回时检查；判定只认资金事实，不接受声明。
    boolean reversalVerified = false;
    if (isBack && featureEnabled) {
      if (hasUnreversedPayment(claimId)) {
        throw ClaimErrors.of(
            ClaimErrorCodes.SC_CLAIM_REVERSAL_REQUIRED, claimId + " 已支付且未冲正，打回前须先完成退款回冲");
      }
      reversalVerified = true; // 非已支付场景：前置空满足，留痕记 1 表示已核验（无需冲正项）
    }

    Date now = new Date();
    ClaimInspectionAction action =
        ClaimInspectionAction.record(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            claimId,
            inspectorId,
            normalized,
            backReason,
            reversalVerified,
            featureEnabled,
            now);

    // 只登记模式（4.3）/ 非打回：不生成重办任务、不改案件状态。
    String taskId = null;
    if (isBack && featureEnabled) {
      var row = claimTaskApplicationService.openForClaim(claimId, TaskType.INSPECTION);
      taskId = row.taskId();
      action.linkTask(Long.parseLong(taskId));
    }
    actionRepository.insert(action);
    return new DispositionView(
        String.valueOf(action.getId()),
        String.valueOf(claimId),
        normalized,
        taskId,
        reversalVerified,
        featureEnabled);
  }

  /** 案件是否仍存在未冲正的已支付结算单（当前阶段为 PAID）。 */
  private boolean hasUnreversedPayment(long claimId) {
    List<SettlementOrder> orders = settlementRepository.findByClaimId(claimId);
    if (orders == null) {
      return false;
    }
    return orders.stream().anyMatch(o -> o != null && o.fundingStageEnum().isPaid());
  }
}
