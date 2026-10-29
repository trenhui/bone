package com.bone.workflow.starter.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 *   <li>中继单轮耗时 / 单条意图耗时（{@code relay.round} / {@code relay.intent.duration}）——意图执行慢、单条卡顿是否拖慢整轮；
 *   <li>发件箱积压深度（{@code outbox.pending_depth}）——at-least-once 中继是否跟得上生产速率，积压持续上涨 = 中继卡死/宿主动作慢的最早信号；
 *   <li>死信积压深度（{@code outbox.dead_letter_depth}）——终态
 *       FAILED（非幂等动作被拒等不可重试根因）的存量，必须有人看见并处置，否则「转人工」只是空话；
 *   <li>意图落地数（executed / failed / terminally_failed）——at-least-once 重投是否健康、是否有卡死；
 *   <li>卡死实例数（{@code instance.stuck}）——RUNNING 且当前节点停留超阈值且无待办任务的实例（看门狗巡检结果）；
 *   <li>通知发送数 / 失败数（P0-3：挂起告警是 fail-closed 闭环的唯一召回手段，失败绝不能只 WARN 看不见）；
 *   <li>挂起告警发送 / 失败——审批流「挂起 → 人看见 → 干预」闭环的可观测性。
 * </ul>
 *
 * <p><b>为什么用 Gauge 而非只记数</b>：积压深度 / 死信存量 / 卡死实例数都是「当前瞬时存量」语义——计数器只能看增量， 看不到「此刻还堆着多少」。存量型指标必须用
 * Gauge，宿主才能设「深度 > N 持续 M 分钟」的告警，而不是等积压把表撑爆才发现。
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

  /** 发件箱积压深度（待 relay 取走的意图数）。Gauge 绑定对象，值由 {@link #outboxPendingDepth} 更新。 */
  private final AtomicInteger pendingDepth = new AtomicInteger(0);

  /** 死信积压深度（终态 FAILED 的存量意图数）。Gauge 绑定对象。 */
  private final AtomicInteger deadLetterDepth = new AtomicInteger(0);

  /** 卡死实例数（看门狗巡检结果）。Gauge 绑定对象。 */
  private final AtomicInteger stuckInstances = new AtomicInteger(0);

  public WorkflowMetrics(MeterRegistry registry) {
    this.registry = registry;
    if (registry != null) {
      // Gauge 必须在注册时即绑定到可变对象，后续只改对象值、不重复 register（重复 register 同名指标会抛异常）。
      Gauge.builder("bone.workflow.outbox.pending_depth", pendingDepth, AtomicInteger::get)
          .description("状态投影发件箱待处理意图积压数（at-least-once 中继未消费）")
          .register(registry);
      Gauge.builder("bone.workflow.outbox.dead_letter_depth", deadLetterDepth, AtomicInteger::get)
          .description("状态投影死信积压数（终态 FAILED，需人工接入幂等后重投或手动处置）")
          .register(registry);
      Gauge.builder("bone.workflow.instance.stuck", stuckInstances, AtomicInteger::get)
          .description("卡死流程实例数（RUNNING 且当前节点停留超阈值且无待办任务）")
          .register(registry);
    }
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

  /** 单条意图执行耗时（毫秒）——比单轮更细：能定位「是某条动作特别慢」而非「整轮都慢」。 */
  public void relayIntentDuration(long millis) {
    if (registry == null) {
      return;
    }
    Timer.builder("bone.workflow.relay.intent.duration")
        .description("单条状态投影意图执行耗时")
        .register(registry)
        .record(millis, TimeUnit.MILLISECONDS);
  }

  /** 发件箱待处理积压深度（Gauge）。每次中继轮询后更新。 */
  public void outboxPendingDepth(int depth) {
    pendingDepth.set(depth);
  }

  /** 死信积压深度（Gauge）。死信巡检作业周期更新。 */
  public void deadLetterDepth(int depth) {
    deadLetterDepth.set(depth);
  }

  /** 卡死实例数（Gauge）。看门狗作业周期更新。 */
  public void stuckInstances(int count) {
    stuckInstances.set(count);
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
