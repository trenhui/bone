package com.bone.workflow.starter.designer;

import com.bone.workflow.domain.ConditionContext;
import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.domain.WorkflowDefinitionValidator;
import com.bone.workflow.domain.WorkflowDefinitionValidator.Violation;
import com.bone.workflow.domain.WorkflowSimulator;
import com.bone.workflow.ports.spi.ApproverResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 发布前试运行：走通路径 + 分支判定明细 + 每节点解析出的审批人。
 *
 * <p><b>为什么这是设计器里 ROI 最高的一块</b>（设计文档 §5.5）：流程配错的三种典型形态——条件写反、兜底边指向错、指派策略参数拼错——
 * 共同点是<b>发布期语法检查全绿、运行期也不报错</b>，只是单据流到了错误的审批人，或者原地挂起。这类问题只能靠「拿真实数据跑一遍」
 * 发现，靠看定义看不出来，靠看线上日志也不会有人去查一张已经通过的单据。
 *
 * <p><b>零副作用</b>：不建实例、不派单、不落库、不通知。它只是把 {@link WorkflowSimulator}（路径与条件求值）和 {@link
 * ApproverResolver}（审批人解析）两个已有能力串起来——<b>不复制</b>任何判定逻辑，否则「模拟说走 A、实际走 B」比不做更糟。
 *
 * <p><b>审批人解析的真实性边界</b>：解析走宿主真正的 {@link ApproverResolver}，因此「组织数据缺失导致解析不出人」也会原样出现。
 * 这正是要暴露的：发布前就能看到「节点 CFO 解析不出人 → 上线即挂起」，而不是等第一张单据卡住。
 */
public class WorkflowSimulationService {

  /**
   * 某节点的审批人解析结果。
   *
   * @param resolved 解析出的账户 id（去重后）
   * @param warnings 该节点的问题（解析不出人 / 解析到发起人本人触发自审批守卫）
   */
  public record NodeApprovers(List<String> resolved, List<String> warnings) {}

  /**
   * 试运行报告。
   *
   * @param path 途经节点编码
   * @param conditionTraces 条件节点判定明细
   * @param approvers 节点编码 → 审批人解析结果（只含路径上的审批 / 抄送节点）
   * @param violations 当前定义的结构违规（不影响试运行继续走，但必须回传）
   * @param warnings 全局提醒
   * @param error 致命错误；{@code null} = 走通
   */
  public record SimulationReport(
      boolean succeeded,
      List<String> path,
      List<WorkflowSimulator.ConditionTrace> conditionTraces,
      Map<String, NodeApprovers> approvers,
      List<Violation> violations,
      List<String> warnings,
      String error) {}

  private final WorkflowSimulator simulator;
  private final WorkflowDefinitionValidator validator;
  private final ApproverResolver approverResolver;
  private final ObjectMapper objectMapper;

  public WorkflowSimulationService(
      WorkflowSimulator simulator,
      WorkflowDefinitionValidator validator,
      ApproverResolver approverResolver,
      ObjectMapper objectMapper) {
    this.simulator = Objects.requireNonNull(simulator, "simulator");
    this.validator = Objects.requireNonNull(validator, "validator");
    this.approverResolver = Objects.requireNonNull(approverResolver, "approverResolver");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  /**
   * 试运行。
   *
   * @param definitionJson 定义 JSON（可以是未发布的草稿）
   * @param sampleData 样例业务数据（按 fieldCode 键；支持 {@code a.b} 路径穿透）
   * @param initiatorId 模拟发起人（组织层级 / 自审批判定都依赖它）
   * @param entityCode 业务实体编码（仅用于装配解析上下文，可空）
   * @param recordId 业务记录 id（同上，可空）
   */
  public SimulationReport simulate(
      String definitionJson,
      Map<String, Object> sampleData,
      String initiatorId,
      String entityCode,
      String recordId) {
    WorkflowDefinition definition = read(definitionJson);
    Map<String, Object> data = sampleData == null ? Map.of() : sampleData;
    List<Violation> violations = validator.validate(definition);
    WorkflowSimulator.Simulation simulation =
        simulator.simulate(definition, data, ConditionContext.of(initiatorId));

    Map<String, NodeApprovers> approvers = new java.util.LinkedHashMap<>();
    List<String> warnings = new ArrayList<>(simulation.warnings());
    if (!violations.isEmpty()) {
      warnings.add("当前定义有 " + violations.size() + " 项结构违规，试运行结论仅供参考；请先按校验清单修正");
    }
    for (String nodeCode : simulation.path()) {
      WorkflowDefinition.Node node = definition.node(nodeCode).orElse(null);
      if (node == null) {
        continue;
      }
      if (node.type() != WorkflowDefinition.NodeType.APPROVE
          && node.type() != WorkflowDefinition.NodeType.CC) {
        continue;
      }
      approvers.put(
          nodeCode, resolveApprovers(definition, node, data, initiatorId, entityCode, recordId));
    }

    return new SimulationReport(
        simulation.succeeded(),
        simulation.path(),
        simulation.conditionTraces(),
        approvers,
        violations,
        warnings,
        simulation.error());
  }

  private NodeApprovers resolveApprovers(
      WorkflowDefinition definition,
      WorkflowDefinition.Node node,
      Map<String, Object> data,
      String initiatorId,
      String entityCode,
      String recordId) {
    List<WorkflowDefinition.AssignmentRule> rules =
        definition.assignments().getOrDefault(node.code(), List.of());
    if (rules.isEmpty()) {
      return new NodeApprovers(List.of(), List.of("节点 " + node.code() + " 未配置指派规则，运行期会挂起"));
    }
    Set<String> resolved = new LinkedHashSet<>();
    List<String> warnings = new ArrayList<>();
    ApproverResolver.ApproverContext context =
        new ApproverResolver.ApproverContext(entityCode, recordId, initiatorId, data);
    for (WorkflowDefinition.AssignmentRule rule : rules) {
      List<String> each;
      try {
        each = approverResolver.resolve(rule, node, context);
      } catch (RuntimeException failure) {
        warnings.add(
            "节点 " + node.code() + " 的指派策略 " + rule.strategy() + " 解析异常：" + failure.getMessage());
        continue;
      }
      if (each == null || each.isEmpty()) {
        warnings.add(
            "节点 " + node.code() + " 的指派策略 " + describe(rule) + " 解析不出人，运行期该节点会挂起（不派单、不报错）");
        continue;
      }
      resolved.addAll(each);
    }
    if (resolved.isEmpty()) {
      return new NodeApprovers(List.of(), warnings);
    }
    if (initiatorId != null && resolved.contains(initiatorId)) {
      // 职责分离守卫（D4）：引擎在派单前判定，命中即「挂起 + 管理员告警」而不是自动跳过发起人。
      // 模拟阶段就提示，避免上线后第一批单据集体挂起。
      warnings.add("节点 " + node.code() + " 会解析到发起人本人（" + initiatorId + "），运行期将触发自审批守卫并挂起");
    }
    return new NodeApprovers(List.copyOf(resolved), warnings);
  }

  private static String describe(WorkflowDefinition.AssignmentRule rule) {
    Map<String, Object> params = rule.params() == null ? Map.of() : rule.params();
    return params.isEmpty() ? rule.strategy() : rule.strategy() + String.valueOf(params);
  }

  private WorkflowDefinition read(String definitionJson) {
    if (definitionJson == null || definitionJson.isBlank()) {
      throw new WorkflowDefinitionRequestException("流程定义 JSON 为空");
    }
    try {
      return objectMapper.readValue(definitionJson, WorkflowDefinition.class);
    } catch (Exception exception) {
      throw new WorkflowDefinitionRequestException(
          "流程定义 JSON 无法解析：" + exception.getMessage(), exception);
    }
  }

  /** 便利：把 {@code $me} 条件用到的发起人也塞进样例数据。 */
  public static Optional<String> initiatorOf(String initiatorId) {
    return Optional.ofNullable(initiatorId).filter(id -> !id.isBlank());
  }
}
