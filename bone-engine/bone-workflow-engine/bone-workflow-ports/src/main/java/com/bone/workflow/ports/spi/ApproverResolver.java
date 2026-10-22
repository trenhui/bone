package com.bone.workflow.ports.spi;

import com.bone.workflow.domain.WorkflowDefinition;
import java.util.List;
import java.util.Map;

/**
 * 审批人指派解析器（端口）。
 *
 * <p>把「指派策略」解析成具体账户 id。宿主实现通常接组织目录（人事系统 / IAM）：
 *
 * <ul>
 *   <li>{@code LINE_MANAGER} — 发起人的直属主管
 *   <li>{@code DEPT_HEAD} — 发起人所在部门负责人
 *   <li>{@code ROLE} — 指定角色（{@code params.roleCode}）的成员
 *   <li>{@code USER} — 指定人（{@code params.userId}）
 *   <li>{@code FIELD} — 业务表单上的某个字段（{@code params.fieldCode}）本身即人
 *   <li>{@code INITIATOR_SELECT} — 发起人自选（由表单指定，运行期读取）
 *   <li>{@code LEVEL_UP} — 发起人向上 N 级（{@code params.level}）
 * </ul>
 *
 * <p><b>解析不出人怎么办</b>：这不是「跳过该节点」的理由（那等于让单据无人审批直接放行），而是<b>挂起</b>——引擎 应把实例置为 {@code SUSPENDED}
 * 并通知管理员补组织架构。返回空列表即表示「解析不出」，由引擎裁决挂起。
 */
public interface ApproverResolver {

  /**
   * 按指派规则解析办理人。
   *
   * @param rule 指派规则
   * @param node 所在节点
   * @param context 解析上下文
   * @return 办理人账户 id 列表（可能为空 = 解析不出）
   */
  List<String> resolve(
      WorkflowDefinition.AssignmentRule rule,
      WorkflowDefinition.Node node,
      ApproverContext context);

  /** 解析上下文。 */
  record ApproverContext(
      String entityCode, String recordId, String initiatorId, Map<String, Object> recordData) {}
}
