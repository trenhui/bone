package com.bone.workflow.starter.adapter;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.ports.spi.WorkflowDefinitionRepository;
import com.bone.workflow.starter.sdk.WfProcessDefRow;
import com.bone.workflow.starter.sdk.WfProcessDefSdkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 流程定义仓储的平台实现（bone-metadata-sdk {@code @Sql} → {@code wf_process_def}）。
 *
 * <p>2026-10-24 自裸 JDBC 收敛到 SDK {@code @Sql}（HC-006 豁免随之移除）。只读：v1 无发布写路径， 定义由迁移 / 种子数据落库。
 *
 * <p><b>租户纪律</b>：{@code @TenantScope(MANUAL)} + 显式谓词，租户由 {@link TenantContext} 提供， 缺失即失败关闭。
 */
public class SdkWorkflowDefinitionRepository implements WorkflowDefinitionRepository {

  private final WfProcessDefSdkRepository definitions;
  private final ObjectMapper objectMapper;

  public SdkWorkflowDefinitionRepository(
      WfProcessDefSdkRepository definitions, ObjectMapper objectMapper) {
    this.definitions = Objects.requireNonNull(definitions, "definitions");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  @Override
  public Optional<WorkflowDefinition> findLatestPublished(String definitionKey) {
    return definitions
        .findLatestPublishedRow(definitionKey, requireTenant())
        .map(this::toDefinition);
  }

  @Override
  public Optional<WorkflowDefinition> findByVersion(String definitionKey, int version) {
    return definitions
        .findByVersionRow(definitionKey, version, requireTenant())
        .map(this::toDefinition);
  }

  @Override
  public List<WorkflowDefinition> listPublished(String definitionKey) {
    return definitions.listPublishedRows(definitionKey, requireTenant()).stream()
        .map(this::toDefinition)
        .toList();
  }

  private WorkflowDefinition toDefinition(WfProcessDefRow row) {
    try {
      return objectMapper.readValue(row.getDefinitionJson(), WorkflowDefinition.class);
    } catch (Exception ex) {
      // 定义 JSON 损坏是「流程跑不了」的硬故障，必须显式炸出，不能静默返回 null 让调用方以为没有定义
      throw new IllegalStateException(
          "流程定义 JSON 无法解析：defKey=" + row.getDefKey() + " version=" + row.getDefVersion(), ex);
    }
  }

  private Long requireTenant() {
    Long tenantId = TenantContext.getTenantIdAsLong();
    if (tenantId == null) {
      throw new IllegalStateException("审批流桥接层缺少租户上下文（TenantContext）——拒绝在未知租户下读取流程定义");
    }
    return tenantId;
  }
}
