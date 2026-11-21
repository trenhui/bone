package com.bone.procurement.governance.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bone.procurement.governance.domain.gateway.CapabilityRegistry;
import com.bone.procurement.governance.domain.gateway.GlobalKillSwitchStore;
import com.bone.procurement.governance.domain.gateway.GovernanceActorPort;
import com.bone.procurement.governance.domain.gateway.GovernanceTracePort;
import com.bone.procurement.governance.domain.gateway.HumanDecisionLedger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** ApplicationContextRunner 断言：治理域四个端口各恰好一个持久化 bean，退役内存实现不参与装配。 */
class GovernanceRepositoryBeanUniquenessTest {

  private final GovernanceActorPort actor = () -> "operator-1";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(
              CapabilityRegistry.class,
              () -> new CapabilityRegistryAdapter(mock(AiCapabilitySdkRepository.class)))
          .withBean(
              HumanDecisionLedger.class,
              () -> new HumanDecisionLedgerAdapter(mock(HumanDecisionSdkRepository.class)))
          .withBean(
              GlobalKillSwitchStore.class,
              () ->
                  new GlobalKillSwitchStoreAdapter(
                      mock(GlobalKillSwitchSdkRepository.class), actor))
          .withBean(
              GovernanceTracePort.class,
              () -> new GovernanceTraceAdapter(mock(GovernanceTraceSdkRepository.class)));

  @Test
  @DisplayName("治理四端口各恰好一个 bean")
  void singlePortBeans() {
    runner.run(
        context -> {
          assertThat(context.getStartupFailure()).isNull();
          assertThat(context).hasSingleBean(CapabilityRegistry.class);
          assertThat(context).hasSingleBean(HumanDecisionLedger.class);
          assertThat(context).hasSingleBean(GlobalKillSwitchStore.class);
          assertThat(context).hasSingleBean(GovernanceTracePort.class);
        });
  }
}
