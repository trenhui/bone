package com.bone.workflow.starter.adapter;

import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.ports.spi.ApproverResolver;
import com.bone.workflow.starter.spi.OrgHierarchyProvider;
import com.bone.workflow.starter.spi.RoleMemberProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 审批人指派解析的<b>默认</b>实现：处理不依赖组织目录的策略，组织相关策略经 {@link RoleMemberProvider} / {@link
 * OrgHierarchyProvider} SPI 外接。
 *
 * <p><b>能力边界</b>：平台 IAM 是<b>独立部署的服务</b>（全仓无模块把它当库消费）。本实现通过两个宿主接入点吸收组织能力：
 *
 * <ul>
 *   <li>{@code ROLE} —— 经 {@link RoleMemberProvider}（如接 IAM 的账号-角色查询端点）。
 *   <li>{@code LINE_MANAGER} / {@code DEPT_HEAD} / {@code LEVEL_UP} —— 经 {@link
 *       OrgHierarchyProvider}（如接 IAM 的组织层级查询端点）。
 *   <li>{@code USER} / {@code FIELD} / {@code INITIATOR_SELECT} —— 不依赖组织目录，开箱即用（如报销的 {@code
 *       approver_id} 字段本身即人）。
 * </ul>
 *
 * <p><b>失败关闭（务必先读）</b>：任一组织策略在宿主未提供对应 SPI（或 SPI 查不到人，例如某员工没有主管）时，都返回<b>空列表</b> ——
 * 引擎据此把实例<b>挂起</b>并通知管理员，<b>绝不</b>跳过节点放行（那等于让单据无人审批直接通过，是审批系统最严重事故形态）。 这与设计文档
 * §4.3「解析不出审批人就挂起，不放行」一致。
 */
public class RecordFieldApproverResolver implements ApproverResolver {

  private static final Logger log = LoggerFactory.getLogger(RecordFieldApproverResolver.class);

  private final RoleMemberProvider roleMemberProvider;
  private final OrgHierarchyProvider orgHierarchyProvider;

  public RecordFieldApproverResolver(RoleMemberProvider roleMemberProvider) {
    this(roleMemberProvider, null); // 仅 ROLE 策略可用；组织层级策略在缺省下挂起
  }

  public RecordFieldApproverResolver(
      RoleMemberProvider roleMemberProvider, OrgHierarchyProvider orgHierarchyProvider) {
    this.roleMemberProvider = roleMemberProvider; // 可为 null：未接入组织目录时 ROLE 策略挂起
    this.orgHierarchyProvider = orgHierarchyProvider; // 可为 null：组织层级策略挂起
  }

  @Override
  public List<String> resolve(
      WorkflowDefinition.AssignmentRule rule,
      WorkflowDefinition.Node node,
      ApproverContext context) {
    String strategy = rule.strategy();
    Map<String, Object> params = rule.params() == null ? Map.of() : rule.params();

    switch (strategy) {
      case "LINE_MANAGER", "DEPT_HEAD", "LEVEL_UP" -> {
        return resolveByOrgHierarchy(strategy, params, node, context.initiatorId());
      }
      case "ROLE" -> {
        return resolveByRole(stringParam(params, "roleCode"), node);
      }
      case "USER" -> {
        return resolveByUser(stringParam(params, "userId"));
      }
      case "FIELD", "INITIATOR_SELECT" -> {
        return resolveFromRecord(stringParam(params, "fieldCode"), context.recordData());
      }
      default -> {
        log.warn("[workflow] 未知指派策略 {}，节点 {} 将挂起", strategy, node.code());
        return List.of();
      }
    }
  }

  /**
   * 组织层级策略：经 {@link OrgHierarchyProvider} 解析。
   *
   * <p>宿主未提供 SPI，或 SPI 查不到人（员工无主管 / 无部门 / 已到顶层仍要再向上），一律返回空列表 → 引擎挂起。 这是失败关闭，不是缺陷。
   */
  private List<String> resolveByOrgHierarchy(
      String strategy,
      Map<String, Object> params,
      WorkflowDefinition.Node node,
      String initiatorId) {
    if (orgHierarchyProvider == null) {
      log.warn(
          "[workflow] 指派策略 {} 需要宿主提供 OrgHierarchyProvider（组织层级未接入），节点 {} 将挂起：initiator={}",
          strategy,
          node.code(),
          initiatorId);
      return List.of();
    }
    Optional<String> resolved =
        switch (strategy) {
          case "LINE_MANAGER" -> orgHierarchyProvider.lineManagerOf(initiatorId);
          case "DEPT_HEAD" -> orgHierarchyProvider.deptHeadOf(initiatorId);
          case "LEVEL_UP" -> orgHierarchyProvider.levelUpOf(
              initiatorId, intParam(params, "level", 1));
          default -> Optional.empty();
        };
    if (resolved.isEmpty() || resolved.get().isBlank()) {
      log.warn(
          "[workflow] 指派策略 {} 解析不出人（组织数据缺失），节点 {} 将挂起：initiator={}",
          strategy,
          node.code(),
          initiatorId);
      return List.of();
    }
    return List.of(resolved.get().trim());
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

  private int intParam(Map<String, Object> params, String key, int fallback) {
    Object value = params.get(key);
    if (value == null) {
      return fallback;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(value.toString().trim());
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }
}
