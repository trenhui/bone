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
   * 解释「为什么没解析出人」，让引擎的挂起原因从「解析不出审批人」升级为可定位、可操作的一句话。
   *
   * <p><b>措辞为什么不把话说死</b>：{@link OrgHierarchyProvider} 的宿主实现把「这人没配主管」与 「组织目录不可达」都收敛成 {@code
   * Optional#empty}（fail-closed
   * 的必然结果——两者都不能放行），解析层因此<b>无法区分</b>二者。若在此断言「你没配主管」，运维会去改一条本来就正确的数据，而真正的故障是网络或凭据。故这里只陈述「查了谁的什么、没查到」，并给出两条排查入口。
   */
  @Override
  public String describeFailure(
      WorkflowDefinition.AssignmentRule rule,
      WorkflowDefinition.Node node,
      ApproverContext context) {
    String strategy = rule.strategy() == null ? "?" : rule.strategy();
    Map<String, Object> params = rule.params() == null ? Map.of() : rule.params();
    String initiator = context.initiatorId() == null ? "(未知)" : context.initiatorId();
    return switch (strategy) {
      case "LINE_MANAGER" -> orgFailure(
          strategy, initiator, "直属主管", "iam_account.manager_id 为空（该账号在汇报链顶层，或尚未配置主管）");
      case "DEPT_HEAD" -> orgFailure(
          strategy,
          initiator,
          "部门负责人",
          "iam_dept.leader_id 为空（所在部门未指定负责人）、该账号未归属任何部门，" + "或负责人正是发起人本人且其本人无主管可上溯（已在汇报链顶层）");
      case "LEVEL_UP" -> orgFailure(
          strategy,
          initiator,
          "向上 " + intParam(params, "level", 1) + " 级主管",
          "汇报链在到达该层级前已到顶，或层级数超出 bone.workflow.org-directory.max-levels");
      case "ROLE" -> roleFailure(stringParam(params, "roleCode"));
      case "USER" -> "策略 USER：未配置 userId（流程定义的指派参数为空）";
      case "FIELD", "INITIATOR_SELECT" -> fieldFailure(
          stringParam(params, "fieldCode"), context.recordData());
      default -> "策略 " + strategy + " 不是引擎已知的指派策略";
    };
  }

  private String orgFailure(String strategy, String initiator, String what, String missing) {
    if (orgHierarchyProvider == null) {
      return strategy
          + "：未取到发起人 "
          + initiator
          + " 的"
          + what
          + "——宿主未接入组织目录（OrgHierarchyProvider 未装配）";
    }
    return strategy
        + "：未取到发起人 "
        + initiator
        + " 的"
        + what
        + "——"
        + missing
        + "；也可能是组织目录不可达（查 IAM 健康与 org-directory 相关 warn 日志）";
  }

  private String roleFailure(String roleCode) {
    if (roleCode == null || roleCode.isBlank()) {
      return "策略 ROLE：未配置 roleCode（流程定义的指派参数为空）";
    }
    if (roleMemberProvider == null) {
      return "策略 ROLE：宿主未接入角色成员查询（RoleMemberProvider 未装配），角色 " + roleCode + " 无处取人";
    }
    return "策略 ROLE：角色 " + roleCode + " 当前没有任何成员";
  }

  private String fieldFailure(String fieldCode, Map<String, Object> recordData) {
    if (fieldCode == null || fieldCode.isBlank()) {
      return "策略 FIELD：未配置 fieldCode（流程定义的指派参数为空）";
    }
    if (recordData == null) {
      return "策略 FIELD：单据字段 " + fieldCode + " 取不到（未能读到单据数据）";
    }
    Object value = recordData.get(fieldCode);
    if (value == null) {
      return "策略 FIELD：单据字段 " + fieldCode + " 为空（申请人未填写该字段）";
    }
    return "策略 FIELD：单据字段 " + fieldCode + " 的值 \"" + value + "\" 不是有效账户 id";
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
    String candidate = resolved.get().trim();
    if (!candidate.equals(initiatorId)) {
      return List.of(candidate);
    }
    // ── 自审批规避：解析到发起人本人时，向上取一级 ──────────────────────────────
    // 这<b>不是</b>「跳过审批节点」（那等于少一道审批，单据无人复核直接放行），而是<b>换一个审批人</b>：
    // 节点仍然存在、仍然必须有人决策，只是从「我审我自己」变成「我的主管审我」。
    //
    // 为什么必须处理：部门负责人自己提交单据时，DEPT_HEAD 必然解析到本人。若不处理，该负责人的
    // 每一张单据都会永久挂在 dept 节点——实测租户 1001 的 24 个真实发起人里有 10 个是部门负责人，
    // 即 42% 的提交会直接卡死（fail-closed 保证了不放行，但产品上是死路）。
    // 业界（飞书/钉钉审批、SAP agent determination 的 escalate）都把「审批人=发起人」定义为
    // 「上溯一级」而非「跳过」——上溯后仍有审批人，内控链条完整。
    //
    // 上溯后仍为本人（manager_id 自环）或无主管（已在汇报链顶层）⇒ 返回空，交给引擎挂起，
    // 继续保持失败关闭：宁可卡住等人工指派，也不让单据无审批通过。
    Optional<String> escalated = orgHierarchyProvider.lineManagerOf(initiatorId);
    if (escalated.isPresent()
        && !escalated.get().isBlank()
        && !escalated.get().trim().equals(initiatorId)) {
      log.info(
          "[workflow] 指派策略 {} 解析到发起人本人，已上溯到其主管：node={} initiator={} escalated={}",
          strategy,
          node.code(),
          initiatorId,
          escalated.get().trim());
      return List.of(escalated.get().trim());
    }
    log.warn(
        "[workflow] 指派策略 {} 解析到发起人本人，且其无可上溯的主管（顶层），节点 {} 将挂起：initiator={}",
        strategy,
        node.code(),
        initiatorId);
    return List.of();
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
