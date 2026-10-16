package com.bone.aiorch.application;

import com.bone.aiorch.domain.hitl.HitlItem;
import com.bone.aiorch.domain.hitl.HitlItemRepository;
import com.bone.aiorch.domain.hitl.HitlStatus;
import com.bone.aiorch.domain.hitl.ReasonType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HITL 人工终审应用服务（AI 中台 §4.2 编排 · HITL 子域）：创建待裁决项 + 裁决（confirm/reject/modify/escalate）+ 处置链回放。
 *
 * <p>业界对位：AI Agent 人工监督终审——AI 只产出建议（{@link HitlItem}），人工裁决后才流入业务状态机；置信度 <0.7 强制进 PENDING 待裁决。裁决原因走
 * {@link ReasonType} 受控字典回流，自由文本不进管道。
 */
public class HitlService {

  private static final Logger log = LoggerFactory.getLogger(HitlService.class);

  /** 置信度阈值：低于此值强制进 PENDING 待人工（详设 §4 阈值基线）。 */
  public static final double CONFIRMATION_THRESHOLD = 0.7;

  private final HitlItemRepository repository;

  public HitlService(HitlItemRepository repository) {
    this.repository = repository;
  }

  /** 创建待裁决项（置信度 <0.7 强制 PENDING；≥0.7 也默认进 PENDING 供低风险场景自动确认扩展）。 */
  public HitlItem create(
      String tenantId,
      String scene,
      String suggestion,
      Object suggestedValue,
      String riskLevel,
      double confidence) {
    HitlItem item =
        HitlItem.create(tenantId, scene, suggestion, suggestedValue, riskLevel, confidence);
    repository.save(item);
    log.info(
        "HITL item created: id={}, scene={}, confidence={}, status={}",
        item.getId(),
        scene,
        confidence,
        HitlStatus.PENDING);
    return item;
  }

  /** 人工确认采纳（裁决人 + 可选回流原因）。 */
  public HitlItem confirm(String tenantId, String itemId, String actor, ReasonType reasonType) {
    HitlItem item = pending(tenantId, itemId);
    item.confirm(actor, reasonType);
    repository.save(item);
    return item;
  }

  /** 人工驳回（必带回流原因 + 备注）。 */
  public HitlItem reject(
      String tenantId, String itemId, String actor, ReasonType reasonType, String comment) {
    HitlItem item = pending(tenantId, itemId);
    item.reject(actor, reasonType, comment);
    repository.save(item);
    return item;
  }

  /** 人工修正后采纳（改值 + 必带回流原因）。 */
  public HitlItem modify(
      String tenantId,
      String itemId,
      String actor,
      Object correctedValue,
      ReasonType reasonType,
      String comment) {
    HitlItem item = pending(tenantId, itemId);
    item.modify(actor, correctedValue, reasonType, comment);
    repository.save(item);
    return item;
  }

  /** 升级人工全程处理（编排超时/超轮触发）。 */
  public HitlItem escalate(String tenantId, String itemId, String actor, String comment) {
    HitlItem item = pending(tenantId, itemId);
    item.escalate(actor, comment);
    repository.save(item);
    return item;
  }

  /** 待裁决列表（PENDING，按场景/风险过滤）。 */
  public List<HitlItem> findPending(String tenantId, String scene, String riskLevel) {
    return repository.findPending(tenantId, scene, riskLevel);
  }

  /** 处置链回放（可复现：谁在何时做了什么裁决、带什么原因）。 */
  public List<HitlItem.Disposition> dispositionHistory(String tenantId, String itemId) {
    HitlItem item = repository.findById(itemId);
    if (item == null) {
      throw new IllegalArgumentException("hitl item not found: " + itemId);
    }
    return item.dispositionHistory();
  }

  private HitlItem pending(String tenantId, String itemId) {
    return repository
        .findPendingById(tenantId, itemId)
        .orElseThrow(
            () -> new IllegalArgumentException("hitl item not pending or not found: " + itemId));
  }
}
