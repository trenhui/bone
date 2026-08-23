package com.bone.system.application.port;

import com.bone.system.domain.schedule.ScheduleTask;

/**
 * 定时任务调度端口（application 依赖的抽象，infrastructure 提供实现）。
 *
 * <p>避免 application 层直接依赖基础设施调度实现（DDD 依赖倒置）。
 */
public interface ScheduleTaskScheduler {

  /** 注册任务（按 CRON 调度）。 */
  void register(ScheduleTask task);

  /** 注销任务。 */
  void cancel(Long taskId);
}
