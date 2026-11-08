package com.bone.claim.infrastructure.evaluation;

import com.bone.claim.application.port.out.AiEvalSampleIndexPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link AiEvalSampleIndexPort} 的进程内实现（2026-11-07 第 11 轮循环复核）。
 *
 * <p><b>为什么需要这一层</b>：{@code AiEvalSampleProjection} 是吃事件、维护易失索引的基础设施组件， 应用层不应直触它（DDD P0-1 /
 * E-13.3）。本适配器把「读索引」契约（application.port.out）与 「索引如何维护」解耦：应用服务只依赖端口接口，二期若把易失索引换成落库视图，只需替换本适配器，
 * 应用层零改动。
 *
 * <p><b>诚实边界</b>：本适配器透传 {@code AiEvalSampleProjection} 的进程内易失索引，重启即清空、多实例各持一份；
 * <b>不是</b>持久化回流台账。权威事实源是已落库的 {@code t_claim_inspection}，经 {@code
 * DiffSampleExportApplicationService} 导出。
 */
@Component
@RequiredArgsConstructor
public class AiEvalSampleIndexPortAdapter implements AiEvalSampleIndexPort {

  private final AiEvalSampleProjection projection;

  @Override
  public List<Entry> snapshot() {
    return projection.snapshot();
  }

  @Override
  public int size() {
    return projection.size();
  }
}
