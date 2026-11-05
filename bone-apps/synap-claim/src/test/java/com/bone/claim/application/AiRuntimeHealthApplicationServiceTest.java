package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.application.AiRuntimeHealthApplicationService.AiRuntimeHealth;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.decision.DecisionGraph;
import com.bone.claim.domain.model.decision.DecisionTraceNode;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import com.bone.core.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AI 运行期健康度的判据：<b>「AI 有没有真的跑」不许被算成好消息</b>。
 *
 * <p>这组用例守的是三件会真实误导运维的事：
 *
 * <ul>
 *   <li><b>「说不清」不能算参与</b>。老 schema 的输出里没有 {@code aiAvailable} 键，把它当成参与成功，参与率会凭空变好；
 *       当成降级，又会捏造一次不存在的故障。只能进第三桶。
 *   <li><b>分母只含已表态节点</b>。把 undetermined 算进分母，「一批说不清的数据」会把参与率稀释成一个看着正常的中间值。
 *   <li><b>空集给 {@code null} 不给 0</b>。{@code 0%} 是「AI 全降级」，{@code null} 是「这段时间没有 AI 调用」——
 *       报表把两者混为一谈，就会在空窗期触发一轮并不存在的排障。
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AiRuntimeHealthApplicationServiceTest {

  private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-11-02T00:00:00Z");

  @Mock private DecisionExecutionRepository decisionExecutionRepository;

  @InjectMocks private AiRuntimeHealthApplicationService service;

  // ===== 分桶：参与 / 降级 / 无法判定 =====

  @Test
  @DisplayName("参与率的分母只含已明确表态的节点，不含无法判定")
  void participationRateUsesOnlyDeterminedNodes() {
    List<DecisionTraceNode> nodes =
        List.of(
            node("{\"aiAvailable\":true}"),
            node("{\"aiAvailable\":false,\"aiUnavailableReason\":\"PROVIDER_TIMEOUT\"}"),
            // 没有 aiAvailable 键：既不是参与，也不是降级
            node("{\"capabilityMissing\":false}"));

    AiRuntimeHealth health = AiRuntimeHealthApplicationService.aggregate(nodes, FROM, TO);

    assertThat(health.nodesScanned()).isEqualTo(3);
    assertThat(health.aiParticipated()).isEqualTo(1);
    assertThat(health.aiDegraded()).isEqualTo(1);
    assertThat(health.aiUndetermined()).isEqualTo(1);
    // 1 / (1 + 1) = 0.5，不是 1 / 3
    assertThat(health.participationRate()).isEqualByComparingTo("0.5000");
  }

  @Test
  @DisplayName("输出不可解析时计入无法判定，并单独暴露 unparsableOutputs")
  void unparsableOutputIsUndetermined() {
    List<DecisionTraceNode> nodes =
        List.of(node("not-json"), node("{\"aiAvailable\":true}"), node(""));

    AiRuntimeHealth health = AiRuntimeHealthApplicationService.aggregate(nodes, FROM, TO);

    assertThat(health.aiUndetermined()).isEqualTo(2);
    assertThat(health.unparsableOutputs()).isEqualTo(2);
    assertThat(health.aiParticipated()).isEqualTo(1);
  }

  @Test
  @DisplayName("三桶对账：参与 + 降级 + 无法判定 == 扫描数")
  void bucketsMustSumToScannedNodes() {
    List<DecisionTraceNode> nodes =
        List.of(
            node("{\"aiAvailable\":true}"),
            node("{\"aiAvailable\":true}"),
            node("{\"aiAvailable\":false,\"aiUnavailableReason\":\"MOCK_NOT_ALLOWED\"}"),
            node("{\"capabilityMissing\":false}"),
            node("not-json"),
            node(""));

    AiRuntimeHealth health = AiRuntimeHealthApplicationService.aggregate(nodes, FROM, TO);

    assertThat(health.aiParticipated()).isEqualTo(2);
    assertThat(health.aiDegraded()).isEqualTo(1);
    assertThat(health.aiUndetermined()).isEqualTo(3);
    assertThat(health.nodesScanned()).isEqualTo(6);
    // 不变式：三桶互斥且完备。后续给节点加第四种状态却漏归类时，这里立刻失配，
    // 而不是让总数悄悄小于扫描数——那会让参与率的分母偏小、比率虚高且总量看着正常。
    assertThat(health.aiParticipated() + health.aiDegraded() + health.aiUndetermined())
        .isEqualTo(health.nodesScanned());
  }

  @Test
  @DisplayName("降级但没带原因码：仍计入降级，原因桶记 UNATTRIBUTED")
  void degradedWithoutReasonLandsInUnattributed() {
    List<DecisionTraceNode> nodes = List.of(node("{\"aiAvailable\":false}"));

    AiRuntimeHealth health = AiRuntimeHealthApplicationService.aggregate(nodes, FROM, TO);

    assertThat(health.aiDegraded()).isEqualTo(1);
    assertThat(health.degradedByReason())
        .containsEntry(AiRuntimeHealthApplicationService.UNATTRIBUTED, 1);
    // 一降级就有一条说不清 ⇒ 归因覆盖率 0%，而不是「没有降级」
    assertThat(health.attributionCoverage()).isEqualByComparingTo("0.0000");
  }

  // ===== 原因码归一 =====

  @Test
  @DisplayName("原因码是空白串时与完全没有原因码同样记 UNATTRIBUTED")
  void blankReasonIsUnattributed() {
    List<DecisionTraceNode> nodes =
        List.of(node("{\"aiAvailable\":false,\"aiUnavailableReason\":\"   \"}"));

    AiRuntimeHealth health = AiRuntimeHealthApplicationService.aggregate(nodes, FROM, TO);

    // 判据用的是 isBlank() 而非 isEmpty()：只有把「空白也算缺失」写进用例，
    // 后续把它简化成 isEmpty() 才会红，否则空白串会静默落进 UNRECOGNIZED 桶。
    assertThat(health.degradedByReason())
        .containsEntry(AiRuntimeHealthApplicationService.UNATTRIBUTED, 1)
        .doesNotContainKey(AiRuntimeHealthApplicationService.UNRECOGNIZED);
    assertThat(health.attributionCoverage()).isEqualByComparingTo("0.0000");
  }

  @Test
  @DisplayName("受控码保留原值，未知码归到 UNRECOGNIZED（不让自由文本进审计面）")
  void reasonCodesAreNormalized() {
    List<DecisionTraceNode> nodes =
        List.of(
            node("{\"aiAvailable\":false,\"aiUnavailableReason\":\"MOCK_NOT_ALLOWED\"}"),
            node("{\"aiAvailable\":false,\"aiUnavailableReason\":\"MOCK_NOT_ALLOWED\"}"),
            node("{\"aiAvailable\":false,\"aiUnavailableReason\":\"SOMETHING_NEW_FROM_FUTURE\"}"),
            node("{\"aiAvailable\":false}"));

    AiRuntimeHealth health = AiRuntimeHealthApplicationService.aggregate(nodes, FROM, TO);

    assertThat(health.degradedByReason())
        .containsEntry("MOCK_NOT_ALLOWED", 2)
        .containsEntry(AiRuntimeHealthApplicationService.UNRECOGNIZED, 1)
        .containsEntry(AiRuntimeHealthApplicationService.UNATTRIBUTED, 1);
    // 4 条降级里 3 条有码（2 + 1）⇒ 0.75
    assertThat(health.attributionCoverage()).isEqualByComparingTo("0.7500");
  }

  // ===== 空集语义 =====

  @Test
  @DisplayName("没有降级时归因覆盖率为 null：不是 0%（0% 表示「降级全都说不清」）")
  void attributionCoverageIsNullWithoutDegradation() {
    AiRuntimeHealth health =
        AiRuntimeHealthApplicationService.aggregate(
            List.of(node("{\"aiAvailable\":true}")), FROM, TO);

    assertThat(health.attributionCoverage()).isNull();
    assertThat(health.participationRate()).isEqualByComparingTo("1.0000");
  }

  @Test
  @DisplayName("空窗口两个比率都为 null，扫描数为 0")
  void emptyWindowGivesNullRates() {
    AiRuntimeHealth health = AiRuntimeHealthApplicationService.aggregate(List.of(), FROM, TO);

    assertThat(health.nodesScanned()).isZero();
    assertThat(health.participationRate()).isNull();
    assertThat(health.attributionCoverage()).isNull();
    assertThat(health.truncated()).isFalse();
  }

  @Test
  @DisplayName("全部无法判定时参与率为 null：说不清不等于没参与、也不等于参与")
  void allUndeterminedGivesNullParticipationRate() {
    AiRuntimeHealth health =
        AiRuntimeHealthApplicationService.aggregate(
            List.of(node("{\"capabilityMissing\":false}"), node("{\"capabilityMissing\":false}")),
            FROM,
            TO);

    assertThat(health.aiUndetermined()).isEqualTo(2);
    assertThat(health.participationRate()).isNull();
  }

  // ===== 第二类缺失：能力缺失与模型降级分开计 =====

  @Test
  @DisplayName("能力缺失独立计数：既可能伴随降级，也可能出现在成功节点上")
  void capabilityMissingIsCountedSeparately() {
    List<DecisionTraceNode> nodes =
        List.of(
            // 模型通了，但知识库没接：参与成功 + 能力缺失
            node(
                "{\"aiAvailable\":true,\"capabilityMissing\":true,"
                    + "\"capabilityReason\":\"KNOWLEDGE_UNAVAILABLE\"}"),
            // 两者同时缺
            node(
                "{\"aiAvailable\":false,\"aiUnavailableReason\":\"MOCK_NOT_ALLOWED\","
                    + "\"capabilityMissing\":true,\"capabilityReason\":\"KNOWLEDGE_UNAVAILABLE\"}"),
            // 能力齐全
            node("{\"aiAvailable\":true,\"capabilityMissing\":false}"));

    AiRuntimeHealth health = AiRuntimeHealthApplicationService.aggregate(nodes, FROM, TO);

    assertThat(health.aiParticipated()).isEqualTo(2);
    assertThat(health.aiDegraded()).isEqualTo(1);
    assertThat(health.capabilityMissing()).isEqualTo(2);
    assertThat(health.capabilityByReason()).containsEntry("KNOWLEDGE_UNAVAILABLE", 2);
    // 能力缺失不能被算成降级：参与率仍是 2/(2+1)
    assertThat(health.participationRate()).isEqualByComparingTo("0.6667");
  }

  @Test
  @DisplayName("能力缺失但没带原因码：仍计能力缺失，原因桶记 UNATTRIBUTED")
  void capabilityMissingWithoutReasonLandsInUnattributed() {
    List<DecisionTraceNode> nodes =
        List.of(node("{\"aiAvailable\":true,\"capabilityMissing\":true}"));

    AiRuntimeHealth health = AiRuntimeHealthApplicationService.aggregate(nodes, FROM, TO);

    assertThat(health.capabilityMissing()).isEqualTo(1);
    assertThat(health.capabilityByReason())
        .containsEntry(AiRuntimeHealthApplicationService.UNATTRIBUTED, 1);
    // 能力缺失不是降级：不能污染参与率，也不能凭空造一次模型故障
    assertThat(health.participationRate()).isEqualByComparingTo("1.0000");
  }

  // ===== 扫描上界 =====

  @Test
  @DisplayName("打满节点上界时标记 truncated：数字是下界，不能当全量")
  void truncationIsFlaggedWhenNodeCapReached() {
    DecisionTraceNode sample = node("{\"aiAvailable\":true}");
    List<DecisionTraceNode> nodes =
        Collections.nCopies(AiRuntimeHealthApplicationService.MAX_NODES, sample);

    AiRuntimeHealth health = AiRuntimeHealthApplicationService.aggregate(nodes, FROM, TO);

    assertThat(health.truncated()).isTrue();
    assertThat(health.nodesScanned()).isEqualTo(AiRuntimeHealthApplicationService.MAX_NODES);
  }

  // ===== 参数护栏 =====

  @Test
  @DisplayName("时间窗超过 31 天直接 400，不静默裁剪")
  void windowLongerThanLimitIsRejected() {
    Instant to = FROM.plusSeconds(32L * 24 * 3600);
    assertThatThrownBy(() -> service.health(FROM, to))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", ClaimErrorCodes.CLAIM_AI_RUNTIME_HEALTH_QUERY_INVALID)
        .hasMessageContaining("时间窗过大");
  }

  @Test
  void reversedWindowIsRejected() {
    assertThatThrownBy(() -> service.health(TO, FROM))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", ClaimErrorCodes.CLAIM_AI_RUNTIME_HEALTH_QUERY_INVALID);
  }

  @Test
  void nullWindowIsRejected() {
    assertThatThrownBy(() -> service.health(null, TO))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", ClaimErrorCodes.CLAIM_AI_RUNTIME_HEALTH_QUERY_INVALID);
  }

  @Test
  @DisplayName("取数用 aiAssess 节点名，不接受调用方自己编的节点名")
  void queriesAiAssessNodeWithRequestedWindow() {
    // 先造夹具再 stub：把 node(...) 写进 thenReturn(...) 会在 stubbing 未完成时又去 stub 另一个 mock
    // （UnfinishedStubbingException），这是本仓踩过两次的坑。
    DecisionTraceNode participated = node("{\"aiAvailable\":true}");
    when(decisionExecutionRepository.findAiAssessNodesInWindow(anyString(), any(), any()))
        .thenReturn(List.of(participated));

    AiRuntimeHealth health = service.health(FROM, TO);

    assertThat(health.aiParticipated()).isEqualTo(1);
    org.mockito.Mockito.verify(decisionExecutionRepository)
        .findAiAssessNodesInWindow(DecisionGraph.AI_ASSESS, FROM, TO);
  }

  // ===== 序列化契约 =====

  @Test
  @DisplayName("null 比率必须出现在 JSON 里，且随响应下发口径说明")
  void nullRateIsSerializedAndNoteExplainsIt() throws Exception {
    String json =
        new ObjectMapper()
            .findAndRegisterModules()
            .writeValueAsString(
                AiRuntimeHealthApplicationService.aggregate(
                    List.of(node("{\"capabilityMissing\":false}")), FROM, TO));

    assertThat(json)
        .contains("\"participationRate\":null")
        .contains("\"attributionCoverage\":null")
        .contains("\"note\"")
        .contains("不是准确率");
  }

  // ===== 夹具 =====

  /**
   * 轨迹节点替身。
   *
   * <p>用 mock 而不是真构造：{@code DecisionTraceNode} 的构造器是包级私有的（只有聚合根能 append），
   * 用例要造「输出不合法」这种生产上不该存在但必须能扛住的数据，只能从 getter 侧注入。
   */
  private static DecisionTraceNode node(String output) {
    DecisionTraceNode node = mock(DecisionTraceNode.class);
    lenient().when(node.getOutput()).thenReturn(output);
    return node;
  }
}
