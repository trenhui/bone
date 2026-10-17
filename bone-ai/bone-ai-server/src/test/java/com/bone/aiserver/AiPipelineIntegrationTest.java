package com.bone.aiserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.application.LlmGatewayService;
import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.event.LlmCallRecorded;
import com.bone.ai.gateway.infrastructure.MockLlmClient;
import com.bone.aiorch.application.OrchestrationApplicationService;
import com.bone.aiorch.application.OrchestrationRequest;
import com.bone.aiorch.application.OrchestrationResult;
import com.bone.airules.application.RuleApplicationService;
import com.bone.airules.application.RuleQueryApplicationService;
import com.bone.airules.domain.gateway.RuleEngine;
import com.bone.airules.domain.model.RuleAsset;
import com.bone.airules.domain.repository.RuleAssetRepository;
import com.bone.airules.domain.repository.RuleAssetVersionRepository;
import com.bone.airules.infrastructure.engine.QlExpressRuleEngine;
import com.bone.aitrace.application.EvidenceQueryApplicationService;
import com.bone.aitrace.domain.gateway.TenantProvider;
import com.bone.aitrace.domain.model.EvidenceSnapshot;
import com.bone.aitrace.domain.model.EvidenceType;
import com.bone.aitrace.domain.repository.EvidenceSnapshotRepository;
import com.bone.aitrace.infrastructure.event.EvidenceTraceListener;
import com.bone.core.domain.DomainEvent;
import com.bone.core.domain.event.DomainEventPublisher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 端到端组合集成测试（纯 JUnit，不 boot SDK / 不连库）：用**真实服务类 + 进程内事件流 + mock 仓储**， 证明五个 bone-ai 库能协同——
 *
 * <ul>
 *   <li>规则命中（hitBlock）⇒ 编排短路，不调 LLM、不发事件、不留痕；
 *   <li>规则未命中 ⇒ 调 LLM ⇒ 网关发布 {@link LlmCallRecorded} ⇒ trace 监听落证据快照 ⇒ 按 traceId 可回放。
 * </ul>
 *
 * <p>各库自身的单测覆盖内部逻辑；本测试专补**跨库协同 + 事件链**（mock 单测覆盖不到的部分）。
 */
class AiPipelineIntegrationTest {

  private RuleAssetRepository ruleRepo;
  private RuleAssetVersionRepository ruleVersionRepo;
  private EvidenceSnapshotRepository evidenceRepo;
  private RuleApplicationService ruleService;
  private RuleQueryApplicationService ruleQueryService;
  private EvidenceQueryApplicationService evidenceQuery;
  private OrchestrationApplicationService orchestration;
  private final List<DomainEvent> published = new ArrayList<>();

  @BeforeEach
  void setUp() {
    ruleRepo = mock(RuleAssetRepository.class);
    ruleVersionRepo = mock(RuleAssetVersionRepository.class);
    evidenceRepo = mock(EvidenceSnapshotRepository.class);

    RuleEngine ruleEngine = new QlExpressRuleEngine();
    ruleService = new RuleApplicationService(ruleRepo, ruleVersionRepo, ruleEngine);
    ruleQueryService = new RuleQueryApplicationService(ruleRepo, ruleVersionRepo);

    final EvidenceTraceListener listener =
        new EvidenceTraceListener(evidenceRepo, (TenantProvider) () -> 42L);
    evidenceQuery = new EvidenceQueryApplicationService(evidenceRepo);

    // 进程内事件总线：gateway 发布 LlmCallRecorded → 直接派发给 trace 监听（等价 Spring @EventListener 的多播）
    DomainEventPublisher bus =
        new DomainEventPublisher() {
          @Override
          public void publish(DomainEvent event) {
            published.add(event);
            if (event instanceof LlmCallRecorded recorded) {
              listener.onLlmCallRecorded(recorded);
            }
          }

          @Override
          public void publishAll(Collection<? extends DomainEvent> events) {
            events.forEach(this::publish);
          }
        };

    LlmClient llmClient = new MockLlmClient();
    LlmGatewayService gateway =
        new LlmGatewayService(
            llmClient, bus, (com.bone.ai.gateway.domain.gateway.TenantProvider) () -> "42");
    orchestration = new OrchestrationApplicationService(ruleService, ruleQueryService, gateway);
  }

  @Test
  void miss_rule_calls_llm_then_trace_persists_and_replays() {
    RuleAsset rule = RuleAsset.create("t1", "R99", "大额", "金额≥1万");
    rule.setId("rule-1");
    rule.publishFirst("amount >= 10000", false, 0, "ops"); // hitBlock=false：命中也不短路
    when(ruleRepo.findActive("t1")).thenReturn(List.of(rule));

    LlmRequest llmRequest = LlmRequest.of("mock-model", List.of(LlmMessage.user("评估此单")));
    OrchestrationRequest request =
        new OrchestrationRequest("t1", List.of(), Map.of("amount", 100.0), llmRequest, true);

    OrchestrationResult result = orchestration.run(request);

    // 未短路、LLM 被调用
    assertThat(result.shortCircuited()).isFalse();
    assertThat(result.llmResponse()).isNotNull();

    // 网关发布 LlmCallRecorded → trace 监听落证（三元组 + 当前租户）
    ArgumentCaptor<EvidenceSnapshot> captor = ArgumentCaptor.forClass(EvidenceSnapshot.class);
    verify(evidenceRepo).insert(captor.capture());
    EvidenceSnapshot saved = captor.getValue();
    assertThat(saved.type()).isEqualTo(EvidenceType.LLM_CALL);
    assertThat(saved.getTenantId()).isEqualTo(42L);
    assertThat(saved.getTraceId()).isNotNull();

    // 回放：按 traceId 能取回该次证据
    when(evidenceRepo.findByTraceId(saved.getTraceId())).thenReturn(List.of(saved));
    assertThat(evidenceQuery.replayByTraceId(saved.getTraceId())).containsExactly(saved);
  }

  @Test
  void hit_block_rule_short_circuits_without_llm_or_evidence() {
    RuleAsset rule = RuleAsset.create("t1", "R42", "拦截", "金额≥1万直接拦截");
    rule.setId("rule-2");
    rule.publishFirst("amount >= 10000", true, 0, "ops"); // hitBlock=true：命中即短路
    when(ruleRepo.findActive("t1")).thenReturn(List.of(rule));

    LlmRequest llmRequest = LlmRequest.of("mock-model", List.of(LlmMessage.user("hi")));
    OrchestrationRequest request =
        new OrchestrationRequest("t1", List.of(), Map.of("amount", 15000.0), llmRequest, true);

    OrchestrationResult result = orchestration.run(request);

    assertThat(result.shortCircuited()).isTrue();
    assertThat(result.blockedRuleCode()).isEqualTo("R42");
    assertThat(result.llmResponse()).isNull();

    // 成本红线：不调 LLM ⇒ 无事件、无留痕
    assertThat(published).isEmpty();
    verify(evidenceRepo, never()).insert(any());
  }
}
