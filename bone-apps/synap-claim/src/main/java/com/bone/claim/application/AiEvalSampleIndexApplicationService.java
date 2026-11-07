package com.bone.claim.application;

import com.bone.claim.application.port.out.AiEvalSampleIndexPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 事件样本运行时索引的只读查询入口（2026-11-07 第 11 轮循环复核）。
 *
 * <p><b>补的是什么缺口</b>：第 10 轮新增的 {@code AiEvalSampleProjection} 有 {@code snapshot()} / {@code
 * size()}，但全仓唯一调用方是它自己的单测——注释宣称「供治理面观测」却没有任何生产消费方，属于「已写但零消费」。 本服务把它接到 Controller
 * 上，让「事件确实被消费过」变成运维可查的事实。
 *
 * <p><b>为什么要这一层</b>：Controller 只依赖 application 层，不直注 infrastructure （分层纪律：{@code adapter →
 * application → domain ← infrastructure}）。 本服务仅依赖 {@link AiEvalSampleIndexPort} 端口接口，由 {@code
 * AiEvalSampleIndexPortAdapter} 注入，故 application 层不依赖 infrastructure 实现（DDD P0-1 / E-13.3）。
 * 这里是纯只读投影，不做任何写操作，故无需领域事件 / 事务。
 *
 * <p><b>诚实边界</b>：返回的是**进程内易失索引**，重启即清空、多实例各持一份。 可回溯 / 可审计的权威事实源是已落库的 {@code t_claim_inspection}， 经
 * {@code DiffSampleExportApplicationService} 与 {@code GET /api/v1/claims/eval/diff-samples}
 * 导出。本端点只回答「本次进程内，DIFF 样本事件有没有被真实消费、消费了多少、按什么归因」， <b>不得</b>被当作评测集、盲标金标准或持久化回流台账。
 */
@Service
@RequiredArgsConstructor
public class AiEvalSampleIndexApplicationService {

  private final AiEvalSampleIndexPort indexPort;

  public Report report() {
    List<AiEvalSampleIndexPort.Entry> entries = indexPort.snapshot();
    return new Report(entries.size(), entries);
  }

  /**
   * @param indexed 进程内已索引的样本事件数（按 fingerprint 幂等去重后）
   * @param samples 只读快照；不做排序承诺（内部为并发 Map）
   */
  public record Report(int indexed, List<AiEvalSampleIndexPort.Entry> samples) {
    public Report {
      samples = samples == null ? List.of() : List.copyOf(samples);
    }
  }
}
