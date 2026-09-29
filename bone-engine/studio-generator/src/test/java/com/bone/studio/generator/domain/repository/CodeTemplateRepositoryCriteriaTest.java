package com.bone.studio.generator.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import org.junit.jupiter.api.Test;

/**
 * 模板可见性口径的契约测试：列表 = 自己的模板 + 平台租户下的内置模板种子。
 *
 * <p>三条不能回退的语义：
 *
 * <ul>
 *   <li>当前租户自己的模板要能查到（隔离不是「什么都查不到」）
 *   <li>平台租户下的内置模板种子要能查到，否则模板选择页为空
 *   <li>但绝不能退回「无租户条件」——那等于回到跨租户读取
 * </ul>
 *
 * <p>这里断言 {@link CodeTemplateRepository#visibleToTenantSql()} 这份文档化口径， 避免后续改写（Criteria /
 * QueryBuilder / @Sql）时静默丢掉平台内置模板。
 */
class CodeTemplateRepositoryCriteriaTest {

  @Test
  void visibleToTenantSqlCoversCurrentAndPlatformTenant() {
    String sql = CodeTemplateRepository.visibleToTenantSql();

    assertThat(sql)
        .contains(":tenantId")
        .contains(" OR ")
        .contains(String.valueOf(CodeTemplateRepository.PLATFORM_TENANT_ID));
  }

  @Test
  void isTenantScoped_notUnfiltered() {
    // 回归防护：必须带租户条件
    assertThat(CodeTemplateRepository.visibleToTenantSql()).contains("tenant_id");
  }

  @Test
  void platformRowsMustBeLimitedToSeedRowsOnly() {
    // 平台行必须带「无创建人」限定，否则租户 0 下残留的孤儿用户数据会被公开给所有租户
    assertThat(CodeTemplateRepository.visibleToTenantSql()).contains("created_by IS NULL");
  }

  @Test
  void sdkCriteriaOrIsBroken_mustNotBeUsedAtRuntime() {
    // 回归防护：SDK 的 Criteria.or(Consumer) 会生成一个 fieldName == null 的原生条件，
    // toSql() 看起来完全正常（假绿），但 BaseRepository#validateCriteriaFields 会无条件校验并抛
    // UndefinedFieldException。因此运行期必须走 QueryBuilder，不得改回 pageByCriteria + or()。
    Criteria<CodeTemplate> broken =
        Criteria.<CodeTemplate>create()
            .or(
                c ->
                    c.eq("tenantId", 1001L)
                        .eq("tenantId", CodeTemplateRepository.PLATFORM_TENANT_ID));

    assertThat(broken.toSql()).contains(" OR ");
    assertThat(broken.getMainConditions()).anyMatch(c -> c.getFieldName() == null);
  }
}
