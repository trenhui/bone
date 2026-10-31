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

  /**
   * 解释「为什么没解析出人」，供引擎把挂起原因写成可定位、可操作的一句话。
   *
   * <p><b>为什么需要它</b>：引擎只能看到「审批人列表为空」，看不到空的原因。若挂起原因只有「审批节点 mgr 解析不出审批人」，运维到场后要在 「补主管 / 补部门负责人 /
   * 修组织目录连通性 / 改流程定义」之间逐个试——而单据此刻正停在半路。 把原因前移到解析层，是因为只有解析层知道自己查了什么、查的是谁的什么数据。
   *
   * <p><b>契约</b>：仅当 {@link #resolve} 返回空时被调用。返回值应说明「哪个策略、谁的什么数据缺失、怎么修」； 返回 {@code null}
   * 表示不提供诊断，引擎退化为只写策略名。 <b>不得</b>在此方法里再次发起远程查询或抛异常——它处在挂起路径上，异常会让挂起本身失败， 把「可见的卡单」变成「不可见的卡单」。
   *
   * <p>以 default 方法提供，是为了不打断既有实现：本接口是函数式接口，宿主大量以 lambda 实现， 加抽象方法会一次性失效。
   */
  default String describeFailure(
      WorkflowDefinition.AssignmentRule rule,
      WorkflowDefinition.Node node,
      ApproverContext context) {
    return null;
  }

  /** 解析上下文。 */
  record ApproverContext(
      String entityCode, String recordId, String initiatorId, Map<String, Object> recordData) {}
}
