package com.bone.claim.application.port.out;

import java.time.Instant;
import java.util.List;

/**
 * 评测样本事件运行时索引（出站端口，只读）。
 *
 * <p><b>为什么要抽象成端口</b>：{@code application} 层不得依赖 {@code infrastructure} 实现（DDD P0-1 / {@code
 * ArchitectureTest.application_no_infrastructure}）。 第 10 轮的 {@code AiEvalSampleProjection} 落在
 * infrastructure，若应用服务直接注入它， 构造参数、字段、返回泛型共 6 处违规。把契约放在 application 层、由基础设施实现， 既守住分层，也让二期替换为「落库索引 /
 * MQ 消费视图」时应用服务零改动 （与既有 {@link AiEvalSamplePort} 同一设计意图）。
 *
 * <p><b>诚实边界</b>：这是进程内易失索引的读端口，重启即清空、多实例各持一份； <b>不是</b>持久化回流台账。权威事实源是已落库的 {@code
 * t_claim_inspection}， 经 {@code DiffSampleExportApplicationService} 导出。
 */
public interface AiEvalSampleIndexPort {

  /** 当前进程内已被真实消费的样本事件只读快照（按 fingerprint 幂等去重）。 */
  List<Entry> snapshot();

  int size();

  /** 端口自身的 DTO，避免 application 层引用 infrastructure 的 record。 */
  record Entry(
      String fingerprint,
      Long claimId,
      String inspectionNo,
      String diffType,
      String sampleType,
      Instant occurredAt) {}
}
