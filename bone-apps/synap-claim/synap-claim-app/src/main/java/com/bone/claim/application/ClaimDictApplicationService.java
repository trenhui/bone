package com.bone.claim.application;

import com.bone.claim.domain.model.adjustment.engine.AdjustmentEngine;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.decision.AiDecisionDisclosure;
import com.bone.claim.domain.security.RejectReasonCodes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 理赔受控码字典服务（单一真源出口）。
 *
 * <p>所有「受控码 → 中文标签」的权威集合由此聚合，经 {@code GET /api/v1/claim-config/dicts} 暴露； 前端 codegen
 * 生成字典模块，彻底消灭手维护双源（详见 {@code doc/ai-product/synapClaim} 单一真源纪律）。
 *
 * <p>每个分类直接读对应的后端枚举 / 常量，不在此二次硬编码取值；新增受控码只需改对应枚举或 {@link ClaimDictLabels}，重新生成前端字典即全量同步。
 */
@Service
@RequiredArgsConstructor
public class ClaimDictApplicationService {

  /** 人工拒赔数字受控码（AdjustmentEngine#rejectionCodeOf 派生的值域）。 */
  private static final List<String> REJECT_REASON_CODES =
      List.of("03", "04", "05", "06", "09", "20", "30", "40");

  /** 自动放款消费结果受控码（AutoApprovalOutcome.reason 静态度）。 */
  private static final List<String> CONSUMPTION_REASON_CODES =
      List.of(
          "APPLIED",
          "AUTO_APPROVE_TOGGLE_OFF",
          "NO_DECISION",
          "CLAIM_NOT_FOUND",
          "ALREADY_SETTLED",
          "CAP_EXCEEDED",
          "DUAL_REVIEW_REQUIRED",
          "RISK_GATE_BLOCKED");

  /** 反欺诈硬闸未过原因码（RuleCheckNodeHandler#fraudGateReason 的值域）。 */
  private static final List<String> FRAUD_GATE_REASON_CODES =
      List.of("ANTI_FRAUD_CAPABILITY_MISSING", "RISK_HIT", "VERIFY_NOT_PASSED", "PASSED");

  public ClaimDicts dicts() {
    Map<String, List<ClaimDictItem>> categories = new LinkedHashMap<>();
    categories.put(ClaimDicts.CLAIM_STATUS, claimStatusItems());
    categories.put(ClaimDicts.AI_INVOLVEMENT, aiInvolvementItems());
    categories.put(ClaimDicts.AI_RIGHTS, aiRightsItems());
    categories.put(
        ClaimDicts.CONSUMPTION_REASON,
        fromCodeList(CONSUMPTION_REASON_CODES, ClaimDictLabels.CONSUMPTION_REASON));
    categories.put(ClaimDicts.ZERO_REASON, zeroReasonItems());
    categories.put(
        ClaimDicts.REJECT_REASON, fromCodeList(REJECT_REASON_CODES, ClaimDictLabels.REJECT_REASON));
    categories.put(
        ClaimDicts.AI_REJECT_REASON,
        fromCodeSet(RejectReasonCodes.ALLOWED, ClaimDictLabels.AI_REJECT_REASON));
    categories.put(
        ClaimDicts.FRAUD_GATE_REASON,
        fromCodeList(FRAUD_GATE_REASON_CODES, ClaimDictLabels.FRAUD_GATE_REASON));
    categories.put(ClaimDicts.PERMISSION, permissionItems());
    return new ClaimDicts(categories);
  }

  private List<ClaimDictItem> claimStatusItems() {
    List<ClaimDictItem> items = new ArrayList<>();
    for (ClaimStatus s : ClaimStatus.values()) {
      items.add(new ClaimDictItem(s.name(), s.displayName()));
    }
    return items;
  }

  private List<ClaimDictItem> aiInvolvementItems() {
    List<ClaimDictItem> items = new ArrayList<>();
    for (AiDecisionDisclosure.Involvement v : AiDecisionDisclosure.Involvement.values()) {
      items.add(new ClaimDictItem(v.name(), ClaimDictLabels.AI_INVOLVEMENT.get(v.name())));
    }
    return items;
  }

  private List<ClaimDictItem> aiRightsItems() {
    List<ClaimDictItem> items = new ArrayList<>();
    for (String code : AiDecisionDisclosure.RIGHTS) {
      items.add(new ClaimDictItem(code, ClaimDictLabels.AI_RIGHTS.get(code)));
    }
    return items;
  }

  private List<ClaimDictItem> zeroReasonItems() {
    Map<String, String> labels = ClaimDictLabels.ZERO_REASON;
    List<ClaimDictItem> items = new ArrayList<>();
    items.add(
        new ClaimDictItem(
            AdjustmentEngine.ZERO_REASONABLE, labels.get(AdjustmentEngine.ZERO_REASONABLE)));
    items.add(
        new ClaimDictItem(AdjustmentEngine.ZERO_DEDUCT, labels.get(AdjustmentEngine.ZERO_DEDUCT)));
    items.add(
        new ClaimDictItem(
            AdjustmentEngine.ZERO_PERCENT, labels.get(AdjustmentEngine.ZERO_PERCENT)));
    items.add(
        new ClaimDictItem(AdjustmentEngine.ZERO_LIMIT, labels.get(AdjustmentEngine.ZERO_LIMIT)));
    items.add(
        new ClaimDictItem(
            AdjustmentEngine.ZERO_LIMIT_EXHAUSTED,
            labels.get(AdjustmentEngine.ZERO_LIMIT_EXHAUSTED)));
    items.add(
        new ClaimDictItem(
            AdjustmentEngine.ZERO_NOT_COVERED, labels.get(AdjustmentEngine.ZERO_NOT_COVERED)));
    return items;
  }

  private List<ClaimDictItem> permissionItems() {
    List<ClaimDictItem> items = new ArrayList<>();
    for (Map.Entry<String, String> e : ClaimDictLabels.PERMISSION.entrySet()) {
      items.add(new ClaimDictItem(e.getKey(), e.getValue()));
    }
    return items;
  }

  private List<ClaimDictItem> fromCodeList(List<String> codes, Map<String, String> labels) {
    List<ClaimDictItem> items = new ArrayList<>(codes.size());
    for (String code : codes) {
      items.add(new ClaimDictItem(code, labels.get(code)));
    }
    return items;
  }

  private List<ClaimDictItem> fromCodeSet(java.util.Set<String> codes, Map<String, String> labels) {
    List<ClaimDictItem> items = new ArrayList<>(codes.size());
    for (String code : codes) {
      items.add(new ClaimDictItem(code, labels.get(code)));
    }
    return items;
  }
}
