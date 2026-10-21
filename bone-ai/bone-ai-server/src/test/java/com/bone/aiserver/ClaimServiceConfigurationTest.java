package com.bone.aiserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.aidoc.domain.gateway.DocumentParser;
import com.bone.ailedger.infrastructure.ledger.InMemoryLimitLedger;
import com.bone.aiorch.application.HitlApplicationService;
import com.bone.aiorch.application.OrchestrationApplicationService;
import com.bone.aiserver.claim.ClaimService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 参考宿主理赔装配验证（ClaimService Bean 依赖修复 + 额度内核可选注入）。
 *
 * <p>这两个用例守的是「参考宿主起得来」这条底线：{@code ClaimController} 依赖 {@link ClaimService}， 缺 Bean 即启动崩溃——
 * 而这个宿主是「产品侧怎么接」的活模板，模板坏了比没有模板更糟。
 */
class ClaimServiceConfigurationTest {

  private final ClaimServiceConfiguration configuration = new ClaimServiceConfiguration();
  private final OrchestrationApplicationService orchestration =
      mock(OrchestrationApplicationService.class);
  private final DocumentParser parser = mock(DocumentParser.class);
  private final HitlApplicationService hitlService = mock(HitlApplicationService.class);

  @Test
  void wires_claim_service_with_ledger_when_present() {
    ObjectProvider<com.bone.ailedger.domain.gateway.LimitLedger> provider = providerMock();
    when(provider.getIfAvailable()).thenReturn(new InMemoryLimitLedger());

    ClaimService service = configuration.claimService(orchestration, parser, hitlService, provider);

    assertThat(service).isNotNull();
  }

  @Test
  void wires_claim_service_without_ledger_when_absent() {
    // bone-ai-ledger 不在类路径 / 无 Bean 时退化为不控额，装配仍成功（能力可插拔）
    ObjectProvider<com.bone.ailedger.domain.gateway.LimitLedger> provider = providerMock();
    when(provider.getIfAvailable()).thenReturn(null);

    ClaimService service = configuration.claimService(orchestration, parser, hitlService, provider);

    assertThat(service).isNotNull();
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> providerMock() {
    return mock(ObjectProvider.class);
  }
}
