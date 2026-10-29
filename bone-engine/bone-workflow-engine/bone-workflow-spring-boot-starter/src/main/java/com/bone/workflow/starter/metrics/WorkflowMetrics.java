package com.bone.workflow.starter.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 审批流 starter 层可观测指标（P1-1）。
 *
 * <p><b>零依赖、可降级</b>：core 仍保持零依赖；本类只在 starter 出现 {@link MeterRegistry}（宿主启用 actuator）时登记指标， 否则全部方法静默
 * no-op——不强制宿主引入 actuator，也不因缺少注册表而报错。
 *
 * <p><b>为什么放 starter 不放 core</b>：指标是「运行期宿主侧」关注点，core 是领域内核（零依赖、可被单测直接驱动）， 指标若进 core 会逼内核反向依赖
 * micrometer。starter 本就依赖 Spring，指标落 starter 不违约。
 *
 * <p>覆盖的关键信号：
 *
 * <ul>
 *   <li>中继单轮耗时 / 意图落地数（executed / failed / terminally_failed）——at-least-once 重投是否健康、是否有卡死；
 *   <li>通知发送数 / 失败数（P0-3：挂起告警是 fail-closed 闭环的唯一召回手段，失败绝不能只 WARN 看不见）；
 *   <li>挂起告警发送 / 失败——审批流「挂起 → 人看见 → 干预」闭环的可观测性。
 * </ul>
 */
public class WorkflowMetrics {

  /** 无注册表时的全局空实例；所有方法 no-op，调用方无感知。 */
  public static final WorkflowMetrics NOOP = new WorkflowMetrics((MeterRegistry) null);

  /**
   * 丢弃用的空注册表：{@link #registry} 为 null 时，计数器登记到它之上——{@link SimpleMeterRegistry}
   * 只在内存中累积、不对外发布，等价于无操作；这样 {@code counter(...).increment()} 永远非空、不抛 NPE， 调用方也无需逐方法判空。
   */
  private static final MeterRegistry NOOP_REGISTRY = new SimpleMeterRegistry();

  private final MeterRegistry registry;

  public WorkflowMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public WorkflowMetrics(ObjectProvider<MeterRegistry> provider) {
    this(provider == null ? null : provider.getIfAvailable());
  }

  private Counter counter(String name, String... tags) {
    MeterRegistry reg = registry == null ? NOOP_REGISTRY : registry;
    Counter.Builder b = Counter.builder(name);
    for (int i = 0; i + 1 < tags.length; i += 2) {
      b = b.tag(tags[i], tags[i + 1]);
    }
    return b.register(reg);
  }

  /** 中继单轮耗时（毫秒）。 */
  public void relayRound(long millis) {
    if (registry == null) {
      return;
    }
    Timer.builder("bone.workflow.relay.round")
        .description("状态投影中继单轮耗时")
        .register(registry)
        .record(millis, TimeUnit.MILLISECONDS);
  }

  public void relayIntentExecuted() {
    counter("bone.workflow.relay.intents", "result", "executed").increment();
  }

  public void relayIntentFailed() {
    counter("bone.workflow.relay.intents", "result", "failed").increment();
  }

  /** 因动作未声明幂等而被<b>终态拒绝</b>的意图数（P0-2 硬约束命中）。 */
  public void relayIntentTerminallyFailed() {
    counter("bone.workflow.relay.intents", "result", "terminally_failed").increment();
  }

  public void notificationSent(String type) {
    if (type == null) {
      type = "unknown";
    }
    counter("bone.workflow.notifications", "type", type, "result", "sent").increment();
  }

  public void notificationFailed() {
    counter("bone.workflow.notifications", "result", "failed").increment();
  }

  public void suspendedAlertSent() {
    counter("bone.workflow.suspended.alerts", "result", "sent").increment();
  }

  public void suspendedAlertFailed() {
    counter("bone.workflow.suspended.alerts", "result", "failed").increment();
  }
}
