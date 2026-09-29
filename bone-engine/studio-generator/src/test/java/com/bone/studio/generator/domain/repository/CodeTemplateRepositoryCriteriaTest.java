package com.bone.studio.generator.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link CodeTemplateRepository#visibleToTenantCriteria} 契约测试：模板列表必须返回「当前租户 + 平台租户」。
 *
 * <p>两条不能回退的语义：
 *
 * <ul>
 *   <li>当前租户自己的模板要能查到（隔离不是「什么都查不到」）
 *   <li>平台租户的内置模板种子要能查到（否则模板选择页为空）
 *   <li>但<strong>不能</strong>退化成无过滤，否则又变回跨租户读取
 * </ul>
 *
 * <p>这里直接断言 {@code Criteria} 生成的 SQL（无需数据库），避免 ORM 语义变更静默改变隔离口径。
 */
class CodeTemplateRepositoryCriteriaTest {

  @Test
  void returnsCurrentTenantOrPlatformTenant() {
    String sql = CodeTemplateRepository.visibleToTenantCriteria(1001L).toSql();

    // 顶层是 OR 组：(tenant_id = :t OR tenant_id = 0)
    assertThat(sql).contains("WHERE").contains("tenant_id").contains(" OR ");
    assertThat(sql).doesNotContain("AND (");

    // 两个租户值都进了参数（当前租户 1001 + 平台租户 0）
    assertThat(CodeTemplateRepository.visibleToTenantCriteria(1001L).getParameters().values())
        .contains(1001L, CodeTemplateRepository.PLATFORM_TENANT_ID);
  }

  @Test
  void isTenantScoped_notUnfiltered() {
    // 回归防护：不能退回「无租户条件」的形态
    String sql = CodeTemplateRepository.visibleToTenantCriteria(1001L).toSql();

    assertThat(sql).isNotBlank().contains("tenant_id");
  }
}
