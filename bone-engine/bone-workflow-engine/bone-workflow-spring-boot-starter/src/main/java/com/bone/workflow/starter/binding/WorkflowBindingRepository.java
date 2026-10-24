package com.bone.workflow.starter.binding;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.starter.sdk.WfBindingRow;
import com.bone.workflow.starter.sdk.WfBindingSdkRepository;
import java.util.Objects;
import java.util.Optional;

/**
 * 业务实体 → 流程定义绑定（{@code wf_binding}）的访问组件。
 *
 * <p>提交动作 handler 经它把 {@code entity_code} 解析成 {@code def_key}——<b>换流程改配置不发版</b> （设计文档 §5.5）。表中没有绑定
 * ⇒ handler 侧 fail-fast，不会静默落到某个默认流程上。
 *
 * <p><b>租户口径</b>：{@code MANUAL} + 显式谓词；<b>无租户上下文即拒绝查询</b>（fail-closed）——
 * 查全租户会让任意租户的提交解析到别家流程，比报错危险得多。
 *
 * <p>多绑定点由 SDK 单结果查询抛 {@code IncorrectResultSizeDataAccessException} 显式失败 （不静默挑一条）——配错流程比报错危险。
 */
public class WorkflowBindingRepository {

  private final WfBindingSdkRepository bindings;

  public WorkflowBindingRepository(WfBindingSdkRepository bindings) {
    this.bindings = Objects.requireNonNull(bindings, "bindings");
  }

  /**
   * 按业务实体编码取生效绑定。
   *
   * @param entityCode 业务实体编码（如 {@code expense_reimbursement}）
   * @return 绑定（含 defKey 与 startAction）；无绑定 / 未启用 / 已删除均返回 empty
   */
  public Optional<WorkflowBinding> findByEntityCode(String entityCode) {
    Long tenantId = TenantContext.getTenantIdAsLong();
    if (tenantId == null) {
      throw new IllegalStateException("审批流绑定查询缺少租户上下文（TenantContext）——拒绝在未知租户下解析流程绑定");
    }
    return bindings.findBindingRow(entityCode, tenantId).map(WorkflowBindingRepository::toBinding);
  }

  /** 行 → 绑定（{@code startAction} 为空时由调用方按「默认提交」解释，这里原样透出）。 */
  private static WorkflowBinding toBinding(WfBindingRow row) {
    return new WorkflowBinding(row.getEntityCode(), row.getDefKey(), row.getStartAction());
  }

  /**
   * 业务实体 → 流程定义的一条绑定。
   *
   * @param entityCode 业务实体编码
   * @param defKey 绑定的流程定义键
   * @param startAction 触发启动的业务动作码（空 = 默认提交动作）
   */
  public record WorkflowBinding(String entityCode, String defKey, String startAction) {}
}
