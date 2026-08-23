package com.bone.system.infrastructure.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 示例定时任务：日志清理占位实现。 */
@Slf4j
@Component
public class LogCleanTaskHandler implements TaskHandler {

  @Override
  public void run(String taskName) {
    log.info("[ScheduleTask] 执行任务 {}：清理过期日志（占位实现）", taskName);
  }
}
