package com.bone.app.expense.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bone.app.expense.ExpenseAdvanceConfirmPayoutHandler;
import com.bone.app.expense.ExpenseReimbursementConfirmPayoutHandler;
import com.bone.app.expense.ExpenseReimbursementInvoiceDedupWriteHook;
import com.bone.app.expense.ExpenseReimbursementWriteHook;
import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import com.bone.metadata.engine.runtime.RuntimeEntityCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** 装配验证：报销应用的 4 个业务组件（2 写钩子 + 2 逃逸处理器）经 autoconfig 注册为 bean。 */
class ExpenseApplicationAutoConfigurationWiringTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ExpenseApplicationAutoConfiguration.class))
          .withBean(NamedParameterJdbcTemplate.class, () -> mock(NamedParameterJdbcTemplate.class))
          .withBean(RuntimeEntityCatalog.class, () -> mock(RuntimeEntityCatalog.class))
          .withBean(JdbcRuntimeRecordService.class, () -> mock(JdbcRuntimeRecordService.class));

  @Test
  void registersExpenseComponents() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasSingleBean(ExpenseReimbursementWriteHook.class);
          assertThat(ctx).hasSingleBean(ExpenseReimbursementInvoiceDedupWriteHook.class);
          assertThat(ctx).hasSingleBean(ExpenseAdvanceConfirmPayoutHandler.class);
          assertThat(ctx).hasSingleBean(ExpenseReimbursementConfirmPayoutHandler.class);
        });
  }
}
