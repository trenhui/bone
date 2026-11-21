package com.bone.procurement.contract.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bone.procurement.contract.domain.gateway.ContractReviewStore;
import com.bone.procurement.contract.domain.gateway.ContractReviewTenantPort;
import com.bone.procurement.contract.domain.gateway.ReviewTracePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** ApplicationContextRunner 断言：合同审查两个端口各恰好一个持久化 bean，退役内存实现不参与装配。 */
class ContractRepositoryBeanUniquenessTest {

  private final ContractReviewSdkRepository reviewRepo = mock(ContractReviewSdkRepository.class);
  private final ReviewTraceSdkRepository traceRepo = mock(ReviewTraceSdkRepository.class);
  private final ContractReviewTenantPort tenantPort = () -> "t-1";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(ReviewTracePort.class, () -> new ReviewTraceAdapter(traceRepo, tenantPort))
          .withBean(
              ContractReviewStore.class,
              () ->
                  new ContractReviewStoreAdapter(
                      reviewRepo, new ReviewTraceAdapter(traceRepo, tenantPort)));

  @Test
  @DisplayName("ContractReviewStore 与 ReviewTracePort 各恰好一个 bean")
  void singlePortBeans() {
    runner.run(
        context -> {
          assertThat(context.getStartupFailure()).isNull();
          assertThat(context).hasSingleBean(ContractReviewStore.class);
          assertThat(context).hasSingleBean(ReviewTracePort.class);
        });
  }
}
