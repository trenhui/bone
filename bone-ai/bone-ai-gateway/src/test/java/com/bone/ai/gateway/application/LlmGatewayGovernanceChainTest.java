package com.bone.ai.gateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmGatewayPolicies;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.ai.gateway.domain.event.LlmCallRecorded;
import com.bone.ai.gateway.domain.event.LlmUsageRecorded;
import com.bone.ai.gateway.domain.gateway.LlmResponseCache;
import com.bone.ai.gateway.domain.gateway.StructuredOutputValidator;
import com.bone.ai.gateway.domain.gateway.TenantProvider;
import com.bone.ai.gateway.domain.gateway.TenantQuotaGuard;
import com.bone.ai.gateway.infrastructure.cache.InMemoryLlmResponseCache;
import com.bone.ai.gateway.infrastructure.quota.InMemoryTenantQuotaGuard;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** 门面治理执行链：配额前置、缓存命中不发事件、输出校验失败也留证、随机请求不缓存。 */
class LlmGatewayGovernanceChainTest {

  private final LlmClient client = mock(LlmClient.class);
  private final DomainEventPublisher publisher = mock(DomainEventPublisher.class);
  private final TenantProvider tenantProvider = mock(TenantProvider.class);

  private LlmGatewayApplicationService service(LlmGatewayPolicies policies) {
    return new LlmGatewayApplicationService(client, publisher, tenantProvider, policies);
  }

  private LlmRequest request(double temperature) {
    return new LlmRequest(
        "m", List.of(LlmMessage.user("q")), temperature, null, false, false, "trace-1");
  }

  @Test
  void quota_exceeded_blocks_before_calling_model() {
    AtomicLong now = new AtomicLong(1_000_000L);
    TenantQuotaGuard quota = new InMemoryTenantQuotaGuard(1, 60_000, now::get);
    when(tenantProvider.currentTenantId()).thenReturn("1");
    when(client.chat(any())).thenReturn(new LlmResponse("v", "m", "stop", new LlmUsage(1, 1, 2)));
    LlmGatewayApplicationService svc = service(new LlmGatewayPolicies(null, null, null, quota));

    svc.invoke(request(0));
    assertThatThrownBy(() -> svc.invoke(request(0)))
        .isInstanceOf(BizException.class)
        .satisfies(e -> assertThat(((BizException) e).getErrorCode()).contains("QUOTA_EXCEEDED"));
    // 超限请求不得触达模型（只成功那一次）
    verify(client, times(1)).chat(any());
  }

  @Test
  void cache_hit_skips_model_and_events() {
    LlmResponseCache cache = new InMemoryLlmResponseCache(10, 60_000);
    when(tenantProvider.currentTenantId()).thenReturn("1");
    when(client.chat(any())).thenReturn(new LlmResponse("v", "m", "stop", new LlmUsage(1, 1, 2)));
    LlmGatewayApplicationService svc = service(new LlmGatewayPolicies(null, null, cache, null));

    LlmResponse first = svc.invoke(request(0));
    LlmResponse second = svc.invoke(request(0));

    assertThat(second.content()).isEqualTo(first.content());
    // 只真实调用一次
    verify(client, times(1)).chat(any());
    // 命中未触达模型：证据与计量事件都只有首次那一条
    verify(publisher, times(1)).publish(any(LlmCallRecorded.class));
    verify(publisher, times(1)).publish(any(LlmUsageRecorded.class));
  }

  @Test
  void random_requests_are_not_cached() {
    LlmResponseCache cache = new InMemoryLlmResponseCache(10, 60_000);
    when(tenantProvider.currentTenantId()).thenReturn("1");
    when(client.chat(any())).thenReturn(new LlmResponse("v", "m", "stop", new LlmUsage(1, 1, 2)));
    LlmGatewayApplicationService svc = service(new LlmGatewayPolicies(null, null, cache, null));

    svc.invoke(request(0.9));
    svc.invoke(request(0.9));

    // temperature>0 输出随机，缓存会改变业务语义 → 必须调两次
    verify(client, times(2)).chat(any());
  }

  @Test
  void output_validation_failure_is_recorded_and_thrown() {
    StructuredOutputValidator validator = mock(StructuredOutputValidator.class);
    org.mockito.Mockito.doThrow(new BizException(502, "bad json", "AI_GATEWAY_OUTPUT_INVALID"))
        .when(validator)
        .validate(anyString(), anyString());
    when(tenantProvider.currentTenantId()).thenReturn("1");
    when(client.chat(any()))
        .thenReturn(new LlmResponse("not json", "m", "stop", new LlmUsage(1, 1, 2)));
    LlmGatewayApplicationService svc = service(new LlmGatewayPolicies(null, validator, null, null));

    LlmRequest jsonReq =
        new LlmRequest("m", List.of(LlmMessage.user("q")), null, null, true, false, "t");

    assertThatThrownBy(() -> svc.invoke(jsonReq)).isInstanceOf(BizException.class);
    // 输出不合规也是「失败调用」，必须留证（否则事故复盘看不到这类故障）
    verify(publisher).publish(any(LlmCallRecorded.class));
    verify(publisher, never()).publish(any(LlmUsageRecorded.class));
  }

  @Test
  void validator_not_invoked_for_non_json_requests() {
    StructuredOutputValidator validator = mock(StructuredOutputValidator.class);
    when(tenantProvider.currentTenantId()).thenReturn("1");
    when(client.chat(any()))
        .thenReturn(new LlmResponse("free text", "m", "stop", new LlmUsage(1, 1, 2)));
    LlmGatewayApplicationService svc = service(new LlmGatewayPolicies(null, validator, null, null));

    svc.invoke(request(0)); // jsonResponse=false

    verify(validator, never()).validate(anyString(), anyString());
  }
}
