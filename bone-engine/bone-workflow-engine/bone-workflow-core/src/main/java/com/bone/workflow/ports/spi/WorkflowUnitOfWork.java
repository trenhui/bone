package com.bone.workflow.ports.spi;

import java.util.function.Supplier;

/**
 * 用例级事务边界（引擎的<b>写路径</b>必须整体原子）。
 *
 * <p><b>为什么单条 SQL 的原子性不够</b>：会签判定、任务 CAS、节点推进各自都已做成单语句原子，但一次「审批决策」是 <b>多语句用例</b>—— 任务置 APPROVED →
 * 节点推进 → 为新节点派单 → 写投影意图。任何两条之间崩一个进程（OOM、发版、kill -9），实例就会停在
 * 「节点已推进、待办没落库」的中间态：<b>零待办、零告警、对账也不管</b>（对账只扫终态投影）。单条 SQL 的原子性对这类窗口 完全无效，只有事务边界能覆盖。
 *
 * <p><b>为什么是端口而不是直接 {@code @Transactional}</b>：core 模块零 Spring 依赖（架构测试守着）， 且引擎要能被非 Spring 宿主复用。故在
 * core 只声明「这里需要一个事务边界」，由装配层用平台事务管理器实现（starter 用 {@code TransactionTemplate}）。
 *
 * <p><b>实现方纪律</b>：
 *
 * <ul>
 *   <li>已有事务时加入外层事务（{@code PROPAGATION_REQUIRED}），不得开嵌套独立事务——否则内层提交后外层回滚会留下半成品。
 *   <li>{@code work} 抛异常必须回滚（{@code RuntimeException} 与 {@code Error} 都要），不得吞掉。
 *   <li>事务内不做远程调用。引擎的 {@link NotificationPort} 若实现为 HTTP 调用，正好落在事务内——装配方应把它做成
 *       落库（表驱动异步投递）或接受「回滚后已发出的通知」这一补偿代价。
 * </ul>
 *
 * <p><b>缺省实现 {@link #DIRECT} 的语义</b>：直接执行、无事务（等价于改造前行为）。它存在的意义是让「没有事务管理器的宿主」 （单测、CLI
 * 回放工具）照常可跑，而不是让生产环境悄悄退化为无事务——生产装配必须显式绑定事务管理器。
 */
public interface WorkflowUnitOfWork {

  /**
   * 在一个事务内执行 {@code work}，返回其结果；异常时回滚并原样抛出。
   *
   * @param work 用例主体（读写仓储 + 端口调用）
   */
  <T> T execute(Supplier<T> work);

  /** 无返回值版本（便于 void 用例）。 */
  default void execute(Runnable work) {
    execute(
        () -> {
          work.run();
          return null;
        });
  }

  /**
   * 无事务直通实现（缺省值）。
   *
   * <p><b>仅供</b>单测与无事务管理器的宿主；生产装配必须替换为事务管理器实现，否则 P0 的崩溃窗口仍然存在。
   */
  WorkflowUnitOfWork DIRECT =
      new WorkflowUnitOfWork() {
        @Override
        public <T> T execute(Supplier<T> work) {
          return work.get();
        }
      };
}
