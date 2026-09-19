package com.bone.studio.generator.domain.gateway;

import com.bone.studio.generator.domain.data.GenerationTask;
import java.util.Optional;

/**
 * 生成任务读侧端口（ADR-0013 / DDD E-4.2）。
 *
 * <p>写侧用例按任务号读取生成任务时经此端口；读侧 DSL（{@code Criteria}）收敛于 infrastructure 实现。
 */
public interface GenerationTaskReadPort {

  /** 按任务号读取生成任务；不存在时返回 {@link Optional#empty()}。 */
  Optional<GenerationTask> findByTaskId(String taskId);
}
