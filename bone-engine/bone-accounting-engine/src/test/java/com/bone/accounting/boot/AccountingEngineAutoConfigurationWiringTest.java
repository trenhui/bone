package com.bone.accounting.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bone.accounting.VoucherGenerateHandler;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import com.bone.metadata.engine.runtime.RuntimeEntityCatalog;
import com.bone.metadata.runtime.application.RuntimeRelationApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 装配验证（对应 bone-metadata-engine-starter 的 WiringTest）：证明会计引擎的 autoconfig 能正确把 {@code
 * voucherGenerateHandler} 注册为 Spring bean，且依赖由宿主（server）提供。
 */
class AccountingEngineAutoConfigurationWiringTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AccountingEngineAutoConfiguration.class))
          .withBean(NamedParameterJdbcTemplate.class, () -> mock(NamedParameterJdbcTemplate.class))
          .withBean(RuntimeEntityCatalog.class, () -> mock(RuntimeEntityCatalog.class))
          .withBean(JdbcRuntimeRecordService.class, () -> mock(JdbcRuntimeRecordService.class))
          .withBean(
              RuntimeRelationApplicationService.class,
              () -> mock(RuntimeRelationApplicationService.class))
          .withBean(TenantProvider.class, () -> mock(TenantProvider.class));

  @Test
  void registersVoucherGenerateHandler() {
    runner.run(ctx -> assertThat(ctx).hasSingleBean(VoucherGenerateHandler.class));
  }
}
