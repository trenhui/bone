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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

  /**
   * HITL SLA 默认时长：分派后 48h 未决 → 升级改派（详设 §4 阈值表第 14 项）。
   *
   * <p>做成常量而非配置项：SLA 是策略，由调用方按险种 / 风险等级显式传入（见带 {@code sla} 参数的重载），
   * 默认值只服务「没想好策略」的场景。库形态模块不自建配置类，避免为一个默认值引入整套 Properties。
   */
  public static final java.time.Duration DEFAULT_SLA = java.time.Duration.ofHours(48);

  /** SLA 熔断的系统执行者标识（非人工裁决，处置链须能区分「系统升级」与「人升级」）。 */
  public static final String SYSTEM_ACTOR_SLA = "system:sla-escalation";

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
   * 已超时未决列表（SLA 熔断的观察面：工作台可据此高亮「即将/已超时」）。
   *
   * <p>时刻取服务端当前时间——SLA 判据必须是服务端权威时钟，不能用调用方传来的时间（否则客户端可伪造未超时）。
   */
  public List<HitlItem> findOverdue(String tenantId, java.time.Duration sla) {
    return repository.findPending(tenantId, null, null).stream()
        .filter(item -> item.isOverdue(LocalDateTime.now(), sla))
        .toList();
  }

  /**
   * 批量升级超时未决项为 {@code ESCALATED}（人工全程处理），返回本次实际升级的项。
   *
   * <p><b>为什么不内置 {@code @Scheduled}</b>：库形态模块没有调度上下文，且多副本部署下定时任务会每副本各跑一遍—— 升级虽由状态机幂等保护（只有 PENDING 可
   * escalate，第二个副本撞 409），但把触发权交给上层（工作台轮询 / 运维定时任务）更可控：上层能按租户分批、能观测扫描结果、能决定升级后是否改派。
   *
   * <p><b>并发安全</b>：单项升级失败（并发已被他人升级 → 409）只记告警不中断整批——一个项的竞争不该让其余项留在超时态。
   */
  public List<HitlItem> escalateOverdue(String tenantId, java.time.Duration sla) {
    List<HitlItem> escalated = new java.util.ArrayList<>();
    for (HitlItem item : findOverdue(tenantId, sla)) {
      try {
        escalated.add(
            escalate(
                tenantId,
                item.getId(),
                SYSTEM_ACTOR_SLA,
                "SLA 超时未决自动升级（sla=" + sla + ", createdAt=" + item.getCreatedAt() + "）"));
      } catch (BizException e) {
        // 并发已被升级 / 已裁决：视为已处理，不中断整批扫描
        log.warn("SLA 升级跳过（并发或状态竞争）: itemId={}, code={}", item.getId(), e.getErrorCode());
      }
    }
    if (!escalated.isEmpty()) {
      log.info("SLA 超时升级完成: tenant={}, escalated={}", tenantId, escalated.size());
    }
    return escalated;
  }

  /** 使用默认 SLA（48h）的批量升级重载。 */
  public List<HitlItem> escalateOverdue(String tenantId) {
    return escalateOverdue(tenantId, DEFAULT_SLA);
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
   * 按 ID 取<b>待裁决</b>项（不含终态；查无则空 Optional，不抛）。
   *
   * <p>2026-10-21 补齐：产品域的额度收尾（确认提交 / 否决释放 / 改额重占）需要在裁决<b>之前</b>读到该待决项的场景与建议金额，才能定位对应的额度占位—— 而
   * {@link #findById} 是主键直查（读终态）且查无即抛 404，不适合作为裁决前的可选读取口。
   *
   * <p>租户口径与仓储一致（{@code findPendingById(tenantId, id)} 本身按租户过滤），故无需再次校验。
   */
  public Optional<HitlItem> findPendingById(String tenantId, String itemId) {
    return repository.findPendingById(tenantId, itemId);
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
