package com.bone.aiorch.application;

import com.bone.aiorch.common.AiOrchErrorCodes;
import com.bone.aiorch.domain.hitl.HitlDisposition;
import com.bone.aiorch.domain.hitl.HitlDispositionRepository;
import com.bone.aiorch.domain.hitl.HitlItem;
import com.bone.aiorch.domain.hitl.HitlItem.Disposition;
import com.bone.aiorch.domain.hitl.HitlItemRepository;
import com.bone.aiorch.domain.hitl.HitlStatus;
import com.bone.aiorch.domain.hitl.ReasonType;
import com.bone.core.exception.BizException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HITL 人工终审应用服务（AI 中台 §4.2 编排 · HITL 子域）：创建待裁决项 + 裁决（confirm/reject/modify/escalate）+ 处置链回放。
 *
 * <p>业界对位：AI Agent 人工监督终审——AI 只产出建议（{@link HitlItem}），人工裁决后才流入业务状态机；置信度 <0.7 强制进 PENDING 待裁决。裁决原因走
 * {@link ReasonType} 受控字典回流，自由文本不进管道。
 *
 * <p><b>留痕语义（2026-10-17 复核修复）</b>：每条裁决除更新 {@code hitl_item} 终态列外，必落一行 {@code hitl_disposition}
 * ——「谁在何时做了什么裁决、带什么原因」是合规硬需求，只在进程内记录会在重启后丢失，审计回放即落空。
 */
public class HitlApplicationService {

  private static final Logger log = LoggerFactory.getLogger(HitlApplicationService.class);

  /** 置信度阈值：低于此值强制进 PENDING 待人工（详设 §4 阈值基线）。 */
  public static final double CONFIRMATION_THRESHOLD = 0.7;

  private final HitlItemRepository repository;
  private final HitlDispositionRepository dispositionRepository;

  public HitlApplicationService(
      HitlItemRepository repository, HitlDispositionRepository dispositionRepository) {
    this.repository = repository;
    this.dispositionRepository = dispositionRepository;
  }

  /** 创建待裁决项（置信度 <0.7 强制 PENDING；≥0.7 也默认进 PENDING 供低风险场景自动确认扩展）。 */
  public HitlItem create(
      String tenantId,
      String scene,
      String suggestion,
      String suggestedValue,
      String riskLevel,
      double confidence,
      String traceId) {
    HitlItem item =
        HitlItem.create(
            tenantId, scene, suggestion, suggestedValue, riskLevel, confidence, traceId);
    repository.save(item);
    log.info(
        "HITL item created: id={}, scene={}, confidence={}, status={}, traceId={}",
        item.getId(),
        scene,
        confidence,
        HitlStatus.PENDING,
        traceId);
    return item;
  }

  /** 人工确认采纳（裁决人 + 可选回流原因）。 */
  public HitlItem confirm(String tenantId, String itemId, String actor, ReasonType reasonType) {
    return act(tenantId, itemId, actor, item -> item.confirm(actor, reasonType));
  }

  /** 人工驳回（必带回流原因 + 备注）。 */
  public HitlItem reject(
      String tenantId, String itemId, String actor, ReasonType reasonType, String comment) {
    return act(tenantId, itemId, actor, item -> item.reject(actor, reasonType, comment));
  }

  /** 人工修正后采纳（改值 + 必带回流原因）。 */
  public HitlItem modify(
      String tenantId,
      String itemId,
      String actor,
      String correctedValue,
      ReasonType reasonType,
      String comment) {
    return act(
        tenantId, itemId, actor, item -> item.modify(actor, correctedValue, reasonType, comment));
  }

  /** 升级人工全程处理（编排超时/超轮触发）。 */
  public HitlItem escalate(String tenantId, String itemId, String actor, String comment) {
    return act(tenantId, itemId, actor, item -> item.escalate(actor, comment));
  }

  /** 待裁决列表（PENDING，按场景/风险过滤）。 */
  public List<HitlItem> findPending(String tenantId, String scene, String riskLevel) {
    return repository.findPending(tenantId, scene, riskLevel);
  }

  /**
   * 按 ID 取待裁决项（含终态——确认台详情 / 状态轮询）。
   *
   * <p>显式租户校验：{@code findById} 是主键直查、不经 SDK 租户过滤注入，不校验即跨租户可读。
   */
  public HitlItem findById(String tenantId, String itemId) {
    HitlItem item = repository.findById(itemId);
    if (item == null || !tenantId.equals(item.getTenantId())) {
      throw new BizException(404, "hitl item not found: " + itemId, AiOrchErrorCodes.NOT_FOUND);
    }
    return item;
  }

  /**
   * 处置链回放（可复现：谁在何时做了什么裁决、带什么原因）。
   *
   * <p>读持久化真相（{@code hitl_disposition}），不依赖进程内状态——重启 / 跨请求 / 换实例均可回放。
   */
  public List<Disposition> dispositionHistory(String tenantId, String itemId) {
    requireItem(tenantId, itemId);
    return dispositionRepository.findByItemId(itemId).stream()
        .map(
            d ->
                new Disposition(
                    d.getAction(),
                    d.getReasonType(),
                    d.getValue(),
                    d.getComment(),
                    d.getActedBy(),
                    d.getActedAt()))
        .toList();
  }

  // ---- internals ----

  @FunctionalInterface
  private interface Decision {
    Disposition apply(HitlItem item);
  }

  /** 统一裁决编排：加载 PENDING 项 → 领域方法（五态约束在域内强制）→ 存终态 → 落处置链。 */
  private HitlItem act(String tenantId, String itemId, String actor, Decision decision) {
    HitlItem item = pending(tenantId, itemId);
    Disposition disposition;
    try {
      disposition = decision.apply(item);
    } catch (IllegalArgumentException e) {
      // 域层受控字典缺失（reject/modify 未带 reason_type）→ 稳定码（门禁：application 层禁裸异常）
      throw new BizException(422, e.getMessage(), AiOrchErrorCodes.REASON_REQUIRED);
    } catch (IllegalStateException e) {
      throw new BizException(409, e.getMessage(), AiOrchErrorCodes.ALREADY_DECIDED);
    }
    repository.save(item);
    dispositionRepository.insert(
        HitlDisposition.of(
            tenantIdOf(item),
            item.getId(),
            disposition.action(),
            disposition.reasonType(),
            disposition.value(),
            disposition.comment(),
            disposition.actor(),
            disposition.actedAt()));
    log.info(
        "HITL item decided: id={}, action={}, status={}, actor={}",
        item.getId(),
        disposition.action(),
        item.getStatus(),
        actor);
    return item;
  }

  /**
   * 待裁决项租户（String）→ 处置链租户列（BIGINT）的显式转换。
   *
   * <p>非数值租户编码无法写入 {@code tenant_id BIGINT}（与 {@code hitl_item} / {@code rule_asset}
   * 同约束）——显式失败而非静默丢租户， 合规留痕不允许出现租户不明的处置记录。
   */
  private Long tenantIdOf(HitlItem item) {
    try {
      return Long.valueOf(item.getTenantId());
    } catch (NumberFormatException e) {
      throw new BizException(
          500,
          "hitl disposition tenant not numeric: " + item.getTenantId(),
          AiOrchErrorCodes.TENANT_NOT_NUMERIC);
    }
  }

  private HitlItem pending(String tenantId, String itemId) {
    return repository
        .findPendingById(tenantId, itemId)
        .orElseThrow(
            () ->
                new BizException(
                    404,
                    "hitl item not pending or not found: " + itemId,
                    AiOrchErrorCodes.NOT_FOUND));
  }

  private void requireItem(String tenantId, String itemId) {
    if (repository.findById(itemId) == null) {
      throw new BizException(404, "hitl item not found: " + itemId, AiOrchErrorCodes.NOT_FOUND);
    }
  }
}
