package com.bone.system.infrastructure.scheduler;

/** 定时任务统一执行目标接口。实现类需注册为 Spring Bean，handler 字段为其 bean 名。 */
public interface TaskHandler {

  /** 执行任务逻辑。 */
  void run(String taskName);
}
