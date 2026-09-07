package com.bone.blueprint.application.orchestration;

import com.bone.blueprint.domain.gateway.OrderOutboxRelayPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Outbox 中继协调层（application 层薄封装）。
 *
 * <p><b>为何存在</b>：定时任务（adapter/schedule）按 E-3 R1 只可依赖 application 层，不可直连 infrastructure 实现类。 本类作为
 * application 入口注入 {@link OrderOutboxRelayPort} 端口，把技术中继动作收敛到一层，供 Job 调用。
 *
 * <p><b>非业务编排、非 E-5.3 业务例外</b>：它不承载任何领域规则或跨聚合事务编排，仅做「端口→调用」的协调转发， 故不触发 E-5.3「跨聚合 Orchestrator 须模块
 * README 登记」的要求。
 */
@Component
@RequiredArgsConstructor
public class OrderOutboxRelayOrchestrator {

  private final OrderOutboxRelayPort relayPort;

  /** 执行一轮 Outbox 中继，返回成功投递条数。 */
  public int relay() {
    return relayPort.relayPending();
  }
}
