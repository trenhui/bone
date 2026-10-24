package com.bone.ai.gateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmGatewayPolicies;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmStreamEvent;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.ai.gateway.domain.event.LlmCallRecorded;
import com.bone.ai.gateway.domain.event.LlmUsageRecorded;
import com.bone.ai.gateway.domain.gateway.ContentSafetyGuard;
import com.bone.ai.gateway.domain.gateway.LlmMetrics;
import com.bone.ai.gateway.domain.gateway.LlmResponseCache;
import com.bone.ai.gateway.domain.gateway.TenantProvider;
import com.bone.ai.gateway.infrastructure.cache.InMemoryLlmResponseCache;
import com.bone.ai.gateway.infrastructure.security.RegexPiiRedactor;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LlmGatewayApplicationServiceTest {

  private final LlmClient client = mock(LlmClient.class);
  private final DomainEventPublisher publisher = mock(DomainEventPublisher.class);
  private final TenantProvider tenantProvider = mock(TenantProvider.class);
  private final LlmGatewayApplicationService service =
      new LlmGatewayApplicationService(client, publisher, tenantProvider);

  @Test
  void invoke_delegates_to_client_and_publishes_event_when_no_redline() {
    LlmRequest req = LlmRequest.of("m", List.of(LlmMessage.user("hi")), "trace-1");
    LlmResponse resp = new LlmResponse("ok", "m", "stop", LlmUsage.empty());
    when(client.chat(req)).thenReturn(resp);
    when(tenantProvider.currentTenantId()).thenReturn("42");

    assertThat(service.invoke(req)).isSameAs(resp);
    verify(client).chat(req);
    verify(publisher).publish(any(LlmCallRecorded.class));
    verify(publisher).publish(any(LlmUsageRecorded.class)); // 计量事件与证据链分离发布
  }

  @Test
  void invoke_publishes_failure_evidence_when_client_fails() {
    // 失败调用也留证（详设 §2.1 执行链第 5 步「含失败」）：事故复盘直接证据
    LlmRequest req = LlmRequest.of("m", List.of(LlmMessage.user("hi")), "trace-2");
    when(client.chat(req)).thenThrow(new IllegalStateException("provider down"));

    assertThatThrownBy(() -> service.invoke(req))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("provider down");
    verify(publisher).publish(any(LlmCallRecorded.class));
    // 失败无用量，不发布计量事件
    verify(publisher, never()).publish(any(LlmUsageRecorded.class));
  }

  @Test
  void invoke_blocks_and_never_calls_client_or_publishes_when_rule_short_circuited() {
    LlmRequest req = LlmRequest.of("m", List.of(LlmMessage.user("hi"))).withRuleShortCircuit();

    assertThatThrownBy(() -> service.invoke(req))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("成本红线");
    // 负向探针：红线命中时绝不触碰模型客户端、也不发布事件
    verifyNoInteractions(client);
    verifyNoInteractions(publisher);
  }

  @Test
  void streamInvoke_streams_raw_tokens_records_ttft_and_publishes_redacted_evidence() {
    // 与 invoke 同口径：面向调用方下发原文（不改写业务答案），PII 脱敏只发生在出站请求与证据落库两处。
    // 故流式 Token 为原文（手机号原样），而证据快照脱敏（掩码手机号）。
    LlmMetrics metrics = mock(LlmMetrics.class);
    LlmGatewayPolicies policies =
        new LlmGatewayPolicies(new RegexPiiRedactor(true), null, null, null, null, metrics, null);
    FakeStreamingLlmClient fake =
        new FakeStreamingLlmClient(
            List.of("我的手机号是", "13800138000", "谢谢"), LlmUsage.empty());
    LlmGatewayApplicationService svc =
        new LlmGatewayApplicationService(fake, publisher, tenantProvider, policies);
    when(tenantProvider.currentTenantId()).thenReturn("42");

    List<LlmStreamEvent> events = new ArrayList<>();
    svc.streamInvoke(LlmRequest.of("m", List.of(LlmMessage.user("u"))), events::add);

    String streamed =
        events.stream()
            .filter(e -> e instanceof LlmStreamEvent.Token)
            .map(e -> ((LlmStreamEvent.Token) e).delta())
            .collect(Collectors.joining());
    assertThat(streamed).contains("13800138000"); // 原文下发
    assertThat(streamed).doesNotContain("138****8000"); // 脱敏标签不泄露给调用方
    assertThat(events.get(events.size() - 1)).isInstanceOf(LlmStreamEvent.Done.class);

    verify(metrics).recordFirstToken(eq("42"), eq("m"), anyLong()); // TTFT 被记录
    ArgumentCaptor<LlmCallRecorded> cap = ArgumentCaptor.forClass(LlmCallRecorded.class);
    verify(publisher).publish(cap.capture());
    assertThat(cap.getValue().response().content()).contains("138****8000"); // 证据链脱敏
    assertThat(cap.getValue().response().content()).doesNotContain("13800138000");
  }

  @Test
  void streamInvoke_content_safety_intercept_emits_error_and_no_done() {
    // 流末内容安全：命中后下发 Error、不发 Done、并失败留证（与 invoke 同口径）
    ContentSafetyGuard unsafe = mock(ContentSafetyGuard.class);
    doThrow(new RuntimeException("含不可接受内容")).when(unsafe).check(anyString(), anyString());
    LlmGatewayPolicies policies =
        new LlmGatewayPolicies(null, null, null, null, null, null, unsafe);
    LlmGatewayApplicationService svc =
        new LlmGatewayApplicationService(
            new FakeStreamingLlmClient(List.of("x"), LlmUsage.empty()),
            publisher,
            tenantProvider,
            policies);
    when(tenantProvider.currentTenantId()).thenReturn("42");

    List<LlmStreamEvent> events = new ArrayList<>();
    svc.streamInvoke(LlmRequest.of("m", List.of(LlmMessage.user("u"))), events::add);

    assertThat(events.stream().anyMatch(e -> e instanceof LlmStreamEvent.Error)).isTrue();
    assertThat(events.stream().noneMatch(e -> e instanceof LlmStreamEvent.Done))
        .isTrue(); // 治理拦截阻断 Done
    // 失败留证（证据链记录被拦截的调用）
    verify(publisher)
        .publish(argThat(e -> e instanceof LlmCallRecorded r && r.errorMessage() != null));
  }

  @Test
  void streamInvoke_deterministic_request_caches_and_second_call_hits_cache() {
    // 确定性请求（temperature 未指定）走精确缓存：第二次命中缓存，不触达模型客户端
    LlmResponseCache cache = new InMemoryLlmResponseCache(10, 60_000L);
    FakeStreamingLlmClient fake =
        new FakeStreamingLlmClient(List.of("hi"), LlmUsage.empty());
    LlmGatewayApplicationService svc =
        new LlmGatewayApplicationService(
            fake, publisher, tenantProvider, new LlmGatewayPolicies(null, null, cache, null, null, null, null));
    when(tenantProvider.currentTenantId()).thenReturn("42");
    LlmRequest req = LlmRequest.of("m", List.of(LlmMessage.user("u")));

    svc.streamInvoke(req, e -> {});
    svc.streamInvoke(req, e -> {});

    assertThat(fake.streamChatCalls).isEqualTo(1); // 第二次命中缓存，未触达客户端
  }

  /** 测试假客户端：按预设增量逐片下发 Token，末段 Done（带用量）。 */
  static final class FakeStreamingLlmClient implements LlmClient {
    final List<String> deltas;
    final LlmUsage usage;
    int streamChatCalls = 0;

    FakeStreamingLlmClient(List<String> deltas, LlmUsage usage) {
      this.deltas = deltas;
      this.usage = usage;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
      return new LlmResponse(String.join("", deltas), request.model(), "stop", usage);
    }

    @Override
    public void streamChat(LlmRequest request, Consumer<LlmStreamEvent> sink) {
      streamChatCalls++;
      deltas.forEach(d -> sink.accept(LlmStreamEvent.token(d)));
      sink.accept(LlmStreamEvent.done(request.model(), "stop", usage));
    }
  }
}
