package com.bone.procurement.qualification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bone.procurement.qualification.domain.gateway.QualificationRepository;
import com.bone.procurement.qualification.domain.repository.OnboardingSqlRepository;
import com.bone.procurement.qualification.domain.repository.QualificationSqlRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** ApplicationContextRunner 断言：准入端口恰好一个持久化 bean，退役内存实现不参与装配。 */
class QualificationRepositoryBeanUniquenessTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(
              QualificationRepository.class,
              () ->
                  new QualificationRepositoryAdapter(
                      mock(OnboardingSqlRepository.class), mock(QualificationSqlRepository.class)));

  @Test
  @DisplayName("QualificationRepository 恰好一个 bean")
  void singlePortBean() {
    runner.run(
        context -> {
          assertThat(context.getStartupFailure()).isNull();
          assertThat(context).hasSingleBean(QualificationRepository.class);
        });
  }
}
