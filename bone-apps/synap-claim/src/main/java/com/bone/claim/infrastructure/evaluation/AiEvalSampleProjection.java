package com.bone.claim.infrastructure.evaluation;

import com.bone.claim.application.port.out.AiEvalSampleIndexPort;
import com.bone.claim.domain.model.evaluation.AiEvalSampleCollectedEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 后督 DIFF 样本事件的真实消费方（2026-11-07 第 10 轮循环复核）。
 *
 * <p><b>补的是什么缺口</b>：{@code InspectionFeedbackRecorder} 产出 {@link AiEvalSampleCollectedEvent}
 * 后，{@code EventAiEvalSamplePortAdapter} 只打日志 + 进程内 publish，全仓此前<b>零消费方</b>——「回流触发器已接线」但没有任何下游动作，
 * 属于典型的「配了不生效」。本类用 {@link EventListener} 真正订阅它，维护一份进程内易失索引。
 *
 * <p><b>为什么是普通 {@code @EventListener} 而不是事务型</b>：事件在 {@code InspectionFeedbackRecorder} 的 {@code
 * AFTER_COMMIT} 回调里、租户上下文内同步发布， 此时事务已提交，{@code @TransactionalEventListener} 不会被触发。
 *
 * <p><b>边界与分层</b>：本类只负责「吃事件、维护索引」，不定义查询契约。应用层读取索引的契约 由 {@link
 * AiEvalSampleIndexPort}（application.port.out）定义，由同包 {@code AiEvalSampleIndexPortAdapter}
 * 实现并注入应用服务。这样 application 层只依赖端口接口、不直触 infrastructure 实现（DDD P0-1 / {@code
 * ArchitectureTest.applicationMustNotDependOnInfrastructure}）， 也满足 E-13.3（infrastructure 实现
 * application 端口的类须命名 *PortAdapter）。
 *
 * <p><b>诚实边界（不得越界宣称）</b>：
 *
 * <ul>
 *   <li>本类维护的索引是<b>进程内、有界、易失</b>的，进程重启即清空、多实例互不相通； <b>不是持久化事实源</b>。可回溯/可审计的权威事实源是已落库的 {@code
 *       t_claim_inspection}（见 {@code DiffSampleExportApplicationService} 与 {@code GET
 *       /api/v1/claims/eval/diff-samples}）。
 *   <li>仓库既有 outbox 兜底对这条链路<b>未接线</b>（{@code SpringDomainEventPublisher} 只做进程内
 *       publish），故不得把本类写成「事件已可靠投递」。
 *   <li>真正的闭环（评测集消费、独立盲标金标准、阈值校准）仍属 Phase B / V2； 迁移 0122 明确要求盲标与后督抽检物理分离，本类不替代它。
 * </ul>
 */
@Slf4j
@Component
public class AiEvalSampleProjection {

  /** 有界：防无界内存增长；超限时按发生时间淘汰最旧一批。 */
  private static final int MAX_ENTRIES = 500;

  /** 索引按 fingerprint 幂等去重；值为端口 DTO，避免 application 层引用 infrastructure 内部类型。 */
  private final Map<String, AiEvalSampleIndexPort.Entry> byFingerprint = new ConcurrentHashMap<>();

  /** 幂等：同一 fingerprint（同租户+同抽检单+同差异类型）只保留一条，重复投递不产生新样本。 */
  @EventListener
  public void onSampleCollected(AiEvalSampleCollectedEvent sample) {
    if (sample == null || sample.fingerprint() == null) {
      return;
    }
    byFingerprint.computeIfAbsent(sample.fingerprint(), key -> toEntry(sample));
    if (byFingerprint.size() > MAX_ENTRIES) {
      evictOldest();
    }
    log.info(
        "[AI_EVAL_SAMPLE_CONSUMED] claimId={} inspectionNo={} diffType={} sampleType={} fingerprint={} "
            + "indexed={}",
        sample.claimId(),
        sample.inspectionNo(),
        sample.diffType(),
        sample.sampleType(),
        sample.fingerprint(),
        byFingerprint.size());
  }

  /** 只读快照：供治理面观测「事件确实被消费」，不写任何业务表。 */
  public List<AiEvalSampleIndexPort.Entry> snapshot() {
    return List.copyOf(byFingerprint.values());
  }

  public int size() {
    return byFingerprint.size();
  }

  private void evictOldest() {
    List<Map.Entry<String, AiEvalSampleIndexPort.Entry>> sorted =
        new ArrayList<>(byFingerprint.entrySet());
    sorted.sort(
        (a, b) -> {
          Instant ta = a.getValue().occurredAt();
          Instant tb = b.getValue().occurredAt();
          if (ta == null) {
            return tb == null ? 0 : -1;
          }
          if (tb == null) {
            return 1;
          }
          return ta.compareTo(tb);
        });
    for (int i = 0; i < sorted.size() - MAX_ENTRIES; i++) {
      byFingerprint.remove(sorted.get(i).getKey());
    }
  }

  private static AiEvalSampleIndexPort.Entry toEntry(AiEvalSampleCollectedEvent sample) {
    return new AiEvalSampleIndexPort.Entry(
        sample.fingerprint(),
        sample.claimId(),
        sample.inspectionNo(),
        sample.diffType(),
        sample.sampleType() == null ? "" : sample.sampleType().name(),
        sample.occurredAt());
  }
}
