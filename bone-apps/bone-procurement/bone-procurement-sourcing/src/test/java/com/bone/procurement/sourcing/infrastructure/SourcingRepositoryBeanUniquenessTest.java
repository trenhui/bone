package com.bone.procurement.sourcing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bone.procurement.sourcing.domain.gateway.SourcingProjectRepository;
import com.bone.procurement.sourcing.domain.repository.RfxSqlRepository;
import com.bone.procurement.sourcing.domain.repository.SourcingProjectSqlRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** ApplicationContextRunner 断言：寻源端口恰好一个持久化 bean，退役内存实现不参与装配。 */
class SourcingRepositoryBeanUniquenessTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(
              SourcingProjectRepository.class,
              () ->
                  new SourcingProjectRepositoryAdapter(
                      mock(SourcingProjectSqlRepository.class), mock(RfxSqlRepository.class)));

  @Test
  @DisplayName("SourcingProjectRepository 恰好一个 bean")
  void singlePortBean() {
    runner.run(
        context -> {
          assertThat(context.getStartupFailure()).isNull();
          assertThat(context).hasSingleBean(SourcingProjectRepository.class);
        });
  }
}
