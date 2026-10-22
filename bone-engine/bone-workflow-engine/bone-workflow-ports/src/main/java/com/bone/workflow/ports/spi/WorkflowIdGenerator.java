package com.bone.workflow.ports.spi;

/**
 * 流程实例 / 任务 id 生成器（端口）。
 *
 * <p>引擎不自行决定 id 形态：宿主可能要求用平台的雪花 id（与业务实体 id 同源、且全局唯一且有序），也可能用 UUID。 由宿主注入实现，引擎只负责调用。
 */
@FunctionalInterface
public interface WorkflowIdGenerator {

  /** 生成下一个 id。 */
  String nextId();
}
