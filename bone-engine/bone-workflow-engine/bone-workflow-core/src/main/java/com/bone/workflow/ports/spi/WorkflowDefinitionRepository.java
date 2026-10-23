package com.bone.workflow.ports.spi;

import com.bone.workflow.domain.WorkflowDefinition;
import java.util.List;
import java.util.Optional;

/**
 * 流程定义仓储（端口）。
 *
 * <p>由宿主实现：把平台元数据实体（{@code wf_process_def} / {@code wf_node_def} / {@code wf_transition_def} /
 * {@code wf_assignment_rule}）装配成 {@link WorkflowDefinition} 快照。
 *
 * <p><b>为什么要端口</b>：审批流引擎不应知道「流程定义存在平台的哪张表里」——那是平台元数据层的职责。端口化后，
 * 引擎既能被平台元数据驱动，也能被别的定义来源驱动（如配置中心、代码内置流程），且引擎模块对 {@code bone-metadata-engine} 零依赖。
 */
public interface WorkflowDefinitionRepository {

  /**
   * 取某个 key 的最新已发布定义。
   *
   * @param definitionKey 流程定义标识
   * @return 定义（无则空）
   */
  Optional<WorkflowDefinition> findLatestPublished(String definitionKey);

  /**
   * 取某个 key 的指定版本定义。
   *
   * @param definitionKey 流程定义标识
   * @param version 版本号
   * @return 定义（无则空）
   */
  Optional<WorkflowDefinition> findByVersion(String definitionKey, int version);

  /**
   * 列出某个 key 的全部已发布版本（供建模界面与版本回溯用）。
   *
   * @param definitionKey 流程定义标识
   */
  List<WorkflowDefinition> listPublished(String definitionKey);
}
