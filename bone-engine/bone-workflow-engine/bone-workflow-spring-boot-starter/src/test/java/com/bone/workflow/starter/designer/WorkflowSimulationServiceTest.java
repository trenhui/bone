package com.bone.workflow.starter.designer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.domain.WorkflowDefinitionValidator;
import com.bone.workflow.domain.WorkflowSimulator;
import com.bone.workflow.ports.spi.ApproverResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 试运行服务的判别力测试：路径 + 分支痕迹 + 审批人解析告警。
 *
 * <p>重点不在「走通」，而在<b>三类上线即挂起的场景在发布前就能看见</b>：策略解析不出人、解析到发起人本人、
 * 定义本身有结构违规。这三条都属于「发布期语法检查全绿、运行期也不报错」的沉默故障。
 */
class WorkflowSimulationServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String DEFINITION =
      """
      {"key":"expense","name":"费用报销","version":1,"engineSchemaVersion":1,
       "nodes":[{"code":"start","type":"START","label":"发起"},
                {"code":"amt","type":"CONDITION","label":"金额判断"},
                {"code":"mgr","type":"APPROVE","mode":"SINGLE","label":"主管"},
                {"code":"cfo","type":"APPROVE","mode":"SINGLE","label":"CFO"},
                {"code":"end","type":"END","label":"结束"}],
       "edges":[{"from":"start","to":"amt"},
                {"from":"amt","to":"mgr","when":{"fieldCode":"amount","operator":"lte","value":5000},"label":"小额"},
                {"from":"amt","to":"cfo","when":null,"label":"默认"},
                {"from":"mgr","to":"end"},{"from":"cfo","to":"end"}],
       "assignments":{"mgr":[{"strategy":"LINE_MANAGER","params":{}}],
                      "cfo":[{"strategy":"ROLE","params":{"roleCode":"cfo"}}]}}
      """;

  /** 解析器桩：ROLE=恰好一人；LINE_MANAGER=空（模拟组织数据缺失）。 */
  private static final ApproverResolver RESOLVER =
      (rule, node, context) ->
          switch (rule.strategy()) {
            case "ROLE" -> List.of("u-cfo");
            case "USER" -> List.of(String.valueOf(rule.params().get("userId")));
            default -> List.of();
          };

  private WorkflowSimulationService service(ApproverResolver resolver) {
    WorkflowSimulator simulator = WorkflowSimulator.withDefaults();
    return new WorkflowSimulationService(
        simulator, new WorkflowDefinitionValidator(), resolver, MAPPER);
  }

  @Test
  @DisplayName("小额 → 走 mgr，并在报告里给出分支判定明细与解析结果")
  void smallAmount_reportsPathTraceAndApprovers() {
    WorkflowSimulationService.SimulationReport report =
        service(RESOLVER).simulate(DEFINITION, Map.of("amount", 3000), "u-init", "expense", "r-1");

    assertThat(report.succeeded()).isTrue();
    assertThat(report.path()).containsExactly("start", "amt", "mgr", "end");
    assertThat(report.conditionTraces()).hasSize(1);
    assertThat(report.conditionTraces().get(0).branches().get(0).matched()).isTrue();
    assertThat(report.approvers()).containsOnlyKeys("mgr");
  }

  @Test
  @DisplayName("组织数据缺失导致解析不出人 → 报告里点名「运行期会挂起」，不是静默空列表")
  void unresolvableApprover_surfacesAsWarning() {
    WorkflowSimulationService.SimulationReport report =
        service(RESOLVER).simulate(DEFINITION, Map.of("amount", 3000), "u-init", "expense", "r-1");

    assertThat(report.approvers().get("mgr").resolved()).isEmpty();
    assertThat(report.approvers().get("mgr").warnings())
        .anyMatch(warning -> warning.contains("挂起"))
        .anyMatch(warning -> warning.contains("LINE_MANAGER"));
  }

  @Test
  @DisplayName("解析到发起人本人 → 提前告警自审批守卫（否则上线后第一批单据集体挂起）")
  void selfApproval_isFlaggedBeforePublish() {
    String definition =
        DEFINITION.replace(
            "{\"strategy\":\"LINE_MANAGER\",\"params\":{}}",
            "{\"strategy\":\"USER\",\"params\":{\"userId\":\"u-init\"}}");
    WorkflowSimulationService.SimulationReport report =
        service(RESOLVER).simulate(definition, Map.of("amount", 3000), "u-init", "expense", "r-1");

    assertThat(report.approvers().get("mgr").resolved()).containsExactly("u-init");
    assertThat(report.approvers().get("mgr").warnings())
        .anyMatch(warning -> warning.contains("自审批"));
  }

  @Test
  @DisplayName("定义有结构违规 → 试运行仍给出路径，但必须回传违规并提示结论仅供参考")
  void structuralViolations_areReportedAlongsideResult() {
    // 把结束节点的类型改掉 ⇒ 整图不再有 END 节点（WF-003），同时该节点无后继（WF-005）。
    // 不用字符串切割删数组元素：文本块里的逗号位置改一次就制造出一个非法 JSON 的假用例。
    String broken = DEFINITION.replace("\"type\":\"END\"", "\"type\":\"CC\"");
    WorkflowSimulationService.SimulationReport report =
        service(RESOLVER).simulate(broken, Map.of("amount", 3000), "u-init", "expense", "r-1");

    assertThat(report.violations())
        .extracting(WorkflowDefinitionValidator.Violation::code)
        .contains("WF-003");
    assertThat(report.warnings()).anyMatch(warning -> warning.contains("仅供参考"));
  }

  @Test
  @DisplayName("坏 JSON → 请求级异常（400），不是悄悄返回空路径")
  void malformedJson_isRequestError() {
    assertThatThrownBy(
            () -> service(RESOLVER).simulate("{not-json", Map.of(), "u-init", null, null))
        .isInstanceOf(WorkflowDefinitionRequestException.class)
        .hasMessageContaining("无法解析");
  }

  @Test
  @DisplayName("试运行零副作用：不落草稿、不落实例（纯函数契约）")
  void simulate_hasNoSideEffects() {
    WorkflowDefinition before = read(DEFINITION);
    service(RESOLVER).simulate(DEFINITION, Map.of("amount", 3000), "u-init", "expense", "r-1");
    assertThat(read(DEFINITION)).isEqualTo(before);
  }

  private static WorkflowDefinition read(String json) {
    try {
      return MAPPER.readValue(json, WorkflowDefinition.class);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
