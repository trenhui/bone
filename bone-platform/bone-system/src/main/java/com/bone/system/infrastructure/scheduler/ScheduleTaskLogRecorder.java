package com.bone.system.infrastructure.scheduler;

import com.bone.system.domain.model.schedule.ScheduleTaskLog;
import com.bone.system.domain.repository.ScheduleTaskLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 执行流水落库器（独立事务）。
 *
 * <p><b>为什么必须 REQUIRES_NEW 而不是跟随调用方事务</b>：手动执行（{@code triggerNow}）跑在 {@code
 * ScheduleTaskApplicationService#runNow} 的事务里，handler 失败时异常一路抛回 ⇒ 调用方事务回滚。
 * 若流水写在同一事务里，**失败执行记录会连同回滚一起消失**—— 「失败可追溯」这个功能恰好在最需要它的场景（失败）失灵。故流水写独立事务：无论调用方事务成败，执行过就是执行过。
 *
 * <p>CRON 路径本无外层事务（调度线程），REQUIRES_NEW 只是为其新建一个并提交，行为不变。
 *
 * <p>同模式见 {@code ConfigHistoryProjector}（配置历史投影的独立事务）。
 */
@Component
@RequiredArgsConstructor
public class ScheduleTaskLogRecorder {

  private final ScheduleTaskLogRepository scheduleTaskLogRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(ScheduleTaskLog entry) {
    scheduleTaskLogRepository.save(entry);
  }
}
