package com.bone.workflow.ports.spi;

import java.util.Map;

/**
 * 业务单据数据读取端口（端口）。
 *
 * <p>流程条件求值需要业务单据的字段值（如「金额 &gt; 5000 走总监审批」）。引擎只声明「我要读数据」，至于这些 数据是存在平台的运行时实体表、还是别处，由宿主实现。
 *
 * <p>注意：这里读的是<b>业务字段</b>，不含流程自身的元数据，避免引擎与业务表结构耦合。
 */
public interface WorkflowRecordPort {

  /**
   * 读取业务单据数据。
   *
   * @param entityCode 业务实体编码
   * @param recordId 业务单据 id
   * @return 字段值映射（按 fieldCode 键；子表以列表形式挂在对应 fieldCode 下）
   */
  Map<String, Object> loadRecordData(String entityCode, String recordId);
}
