package com.bone.system.application.port.out;

import com.bone.system.domain.model.schedule.ScheduleTask;

/**
 * 定时任务调度出站端口（技术能力端口，E-10.2）。
 *
 * <p><b>为什么是 {@code *Port} 而不是 {@code *Scheduler} / {@code *Service}</b>：E-13.2 规定 {@code
 * application/port/out} 下的出站端口一律 {@code *Port} 后缀——端口是「我要什么能力」的契约，不应携带 实现方式（{@code
 * Scheduler}）或层次（{@code Service}）信息。命名换了名字并没有改变调用方写的代码行数， 但让「这是出站边界」这件事在扫描包结构时一眼可见。
 *
 * <p>application 只依赖这个接口，不依赖 Spring {@code ThreadPoolTaskScheduler}：调度机制替换（Quartz /
 * 分布式任务平台）时本层零改动（CORE-02 依赖倒置）。
 */
public interface ScheduleTaskSchedulerPort {

  /**
   * 注册任务（按 CRON 调度）。
   *
   * <p>重复注册同一 id 是安全的：实现须先注销旧句柄再注册，保证一个任务只有一个 {@code ScheduledFuture}。
   */
  void register(ScheduleTask task);

  /**
   * 注销任务。
   *
   * <p>幂等：任务未注册时静默返回——disable / delete / update 三条路径都会调用它，调用方不必先问「有没有」。
   */
  void cancel(Long taskId);
}
