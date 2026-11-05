package com.bone.claim.application;

import java.util.List;
import java.util.Map;

/**
 * 理赔受控码字典全集（单一真源，经 {@code GET /api/v1/claim-config/dicts} 暴露）。
 *
 * <p>分类键名稳定，前端 codegen 按 key 生成对应字典模块；新增/下线受控码只需改后端 {@link ClaimConfigApplicationService} 与 {@link
 * ClaimDictLabels}，前端重新生成即同步，不再逐文件手工对齐。
 */
public record ClaimDicts(Map<String, List<ClaimDictItem>> categories) {

  /** 案件状态（落库用 name，对外用 code；此处 code 字段给对外桥接码，label 给展示名）。 */
  public static final String CLAIM_STATUS = "claimStatus";

  /** AI 参与三态。 */
  public static final String AI_INVOLVEMENT = "aiInvolvement";

  /** PIPL 第二十四条 / 金发 8 号权利受控码。 */
  public static final String AI_RIGHTS = "aiRights";

  /** 自动放款消费结果（AutoApprovalOutcome.reason）。 */
  public static final String CONSUMPTION_REASON = "consumptionReason";

  /** 理算零赔付归因码（ZERO_*）。 */
  public static final String ZERO_REASON = "zeroReason";

  /** 人工拒赔数字受控码（rejectionCodeOf 派生）。 */
  public static final String REJECT_REASON = "rejectReason";

  /** AI 结构化拒赔原因码（RejectReasonCodes 白名单）。 */
  public static final String AI_REJECT_REASON = "aiRejectReason";

  /** 反欺诈硬闸未过原因码（RuleCheckNodeHandler#fraudGateReason）。 */
  public static final String FRAUD_GATE_REASON = "fraudGateReason";

  /** 方法级权限码（@PreAuthorize）。 */
  public static final String PERMISSION = "permission";
}
