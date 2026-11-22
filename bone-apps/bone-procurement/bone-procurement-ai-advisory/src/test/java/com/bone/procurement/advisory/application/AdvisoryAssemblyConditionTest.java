package com.bone.procurement.advisory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.advisory.adapter.web.controller.AdvisoryController;
import com.bone.procurement.governance.application.AiGovernanceApplicationService;
import com.bone.procurement.governance.application.AiGovernanceProperties;
import com.bone.procurement.governance.application.SafetyApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配一致性行为探针：不是反射检查注解，而是真实启动两态上下文，证明默认关闭时 advisory 控制器/服务均不注册， 开启时两者恰好各一个且依赖可满足——避免"控制器在、依赖 Bean
 * 不在"的默认启动断点。
 */
class AdvisoryAssemblyConditionTest {

  private static final String ENABLE = AiGovernanceProperties.PREFIX + ".enabled=true";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(GovernanceDeps.class)
          .withBean(AdvisoryApplicationService.class)
          .withBean(AdvisoryController.class);

  @Test
  @DisplayName("默认关闭：advisory 服务与控制器均不注册")
  void closedRegistersNothing() {
    runner.run(
        context -> {
          assertThat(context).doesNotHaveBean(AdvisoryApplicationService.class);
          assertThat(context).doesNotHaveBean(AdvisoryController.class);
        });
  }

  @Test
  @DisplayName("显式开启：advisory 服务与控制器各一个且依赖可满足")
  void openRegistersExactlyOneEach() {
    runner
        .withPropertyValues(ENABLE)
        .run(
            context -> {
              assertThat(context).hasSingleBean(AdvisoryApplicationService.class);
              assertThat(context).hasSingleBean(AdvisoryController.class);
              assertThat(context).hasSingleBean(AiGovernanceApplicationService.class);
              assertThat(context).hasSingleBean(SafetyApplicationService.class);
            });
  }

  /** 治理依赖替身，证明开启态依赖可满足，不引入真实存储。 */
  @Configuration(proxyBeanMethods = false)
  static class GovernanceDeps {
    @Bean
    AiGovernanceApplicationService governance() {
      return org.mockito.Mockito.mock(AiGovernanceApplicationService.class);
    }

    @Bean
    SafetyApplicationService safety() {
      return org.mockito.Mockito.mock(SafetyApplicationService.class);
    }
  }
}
