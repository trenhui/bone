package com.bone.procurement.risk.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bone.procurement.risk.domain.gateway.RiskAlertRepository;
import com.bone.procurement.risk.domain.repository.PerformanceScoreSqlRepository;
import com.bone.procurement.risk.domain.repository.RiskAlertSqlRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** ApplicationContextRunner 断言：风险端口恰好一个持久化 bean，退役内存实现不参与装配。 */
class RiskRepositoryBeanUniquenessTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(
              RiskAlertRepository.class,
              () ->
                  new RiskAlertRepositoryAdapter(
                      mock(RiskAlertSqlRepository.class),
                      mock(PerformanceScoreSqlRepository.class)));

  @Test
  @DisplayName("RiskAlertRepository 恰好一个 bean")
  void singlePortBean() {
    runner.run(
        context -> {
          assertThat(context.getStartupFailure()).isNull();
          assertThat(context).hasSingleBean(RiskAlertRepository.class);
        });
  }
}
