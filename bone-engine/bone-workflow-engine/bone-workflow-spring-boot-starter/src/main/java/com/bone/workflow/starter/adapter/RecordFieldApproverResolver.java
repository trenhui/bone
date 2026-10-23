package com.bone.workflow.starter.adapter;

import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.ports.spi.ApproverResolver;
import com.bone.workflow.starter.spi.RoleMemberProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 审批人指派解析的<b>默认</b>实现：处理不依赖组织目录的策略，组织相关策略经 {@link RoleMemberProvider} SPI 外接。
 *
 * <p><b>诚实的能力边界（务必先读）</b>：平台 IAM 是<b>独立部署的服务</b>（全仓无模块把它当库消费），且当前 IAM <b>没有「直属主管 / 部门负责人 /
 * 组织层级」字段</b>——{@code Dept} 只有 name/parentId/orderNo。因此：
 *
 * <ul>
 *   <li>{@code LINE_MANAGER} / {@code DEPT_HEAD} / {@code LEVEL_UP} <b>解析不出人</b>（返回空列表）→ 引擎把实例
 *       <b>挂起</b>并通知宿主，而不是跳过节点放行。这是刻意选择的失败关闭，不是缺陷。
 *   <li>要真正支持「直属主管审批」，需先在 IAM 增加组织层级字段（{@code leader_id} 等）与查询端点，再由宿主实现 相应的指派策略——属独立变更。
 *   <li>{@code ROLE} 需要宿主提供 {@link RoleMemberProvider}（如接 IAM 的账号-角色查询端点）；未提供则同样挂起。
 * </ul>
 *
 * <p>当前开箱可用的策略：{@code USER}（指定人）、{@code FIELD} / {@code INITIATOR_SELECT}（业务表单字段本身即人， 如报销的 {@code
 * approver_id}）。
 */
public class RecordFieldApproverResolver implements ApproverResolver {

  private static final Logger log = LoggerFactory.getLogger(RecordFieldApproverResolver.class);

  /** IAM 无组织层级字段，这些策略解析不出人（→ 引擎挂起，而非放行）。 */
  private static final Set<String> UNSUPPORTED_ORG_STRATEGIES =
      Set.of("LINE_MANAGER", "DEPT_HEAD", "LEVEL_UP");

  private final RoleMemberProvider roleMemberProvider;

  public RecordFieldApproverResolver(RoleMemberProvider roleMemberProvider) {
    this.roleMemberProvider = roleMemberProvider; // 可为 null：未接入组织目录时 ROLE 策略挂起
  }

  @Override
  public List<String> resolve(
      WorkflowDefinition.AssignmentRule rule,
      WorkflowDefinition.Node node,
      ApproverContext context) {
    String strategy = rule.strategy();
    Map<String, Object> params = rule.params() == null ? Map.of() : rule.params();

    if (UNSUPPORTED_ORG_STRATEGIES.contains(strategy)) {
      // 显式记录：挂起的根因要能在日志里看见，否则运维只看到「卡单」不知道是策略不受支持
      log.warn(
          "[workflow] 指派策略 {} 暂不受支持（平台无组织层级字段），节点 {} 将挂起：initiator={}",
          strategy,
          node.code(),
          context.initiatorId());
      return List.of();
    }

    return switch (strategy) {
      case "ROLE" -> resolveByRole(stringParam(params, "roleCode"), node);
      case "USER" -> resolveByUser(stringParam(params, "userId"));
      case "FIELD", "INITIATOR_SELECT" -> resolveFromRecord(
          stringParam(params, "fieldCode"), context.recordData());
      default -> {
        log.warn("[workflow] 未知指派策略 {}，节点 {} 将挂起", strategy, node.code());
        yield List.of();
      }
    };
  }

  private List<String> resolveByRole(String roleCode, WorkflowDefinition.Node node) {
    if (roleCode == null || roleCode.isBlank()) {
      return List.of();
    }
    if (roleMemberProvider == null) {
      log.warn(
          "[workflow] ROLE 指派策略需要宿主提供 RoleMemberProvider（组织目录未接入），节点 {} 将挂起：role={}",
          node.code(),
          roleCode);
      return List.of();
    }
    List<String> members = roleMemberProvider.accountIdsByRoleCode(roleCode);
    return members == null ? List.of() : members;
  }

  /** 指定人（原样指派；账号是否存在由平台状态机/办理时校验，桥接层不额外查组织目录）。 */
  private List<String> resolveByUser(String userId) {
    if (userId == null || userId.isBlank()) {
      return List.of();
    }
    return List.of(userId.trim());
  }

  /** 业务表单字段本身即人（报销的 approver_id、发起人自选审批人）。 */
  private List<String> resolveFromRecord(String fieldCode, Map<String, Object> recordData) {
    if (fieldCode == null || fieldCode.isBlank() || recordData == null) {
      return List.of();
    }
    Object value = recordData.get(fieldCode);
    if (value == null) {
      return List.of();
    }
    Set<String> assignees = new LinkedHashSet<>();
    if (value instanceof Collection<?> collection) {
      for (Object each : collection) {
        if (each != null && !each.toString().isBlank()) {
          assignees.add(each.toString());
        }
      }
    } else if (!value.toString().isBlank()) {
      assignees.add(value.toString());
    }
    return new ArrayList<>(assignees);
  }

  private String stringParam(Map<String, Object> params, String key) {
    Object value = params.get(key);
    return value == null ? null : value.toString();
  }
}
