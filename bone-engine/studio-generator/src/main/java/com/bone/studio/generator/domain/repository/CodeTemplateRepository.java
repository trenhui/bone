package com.bone.studio.generator.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import java.util.function.Consumer;

public interface CodeTemplateRepository extends Repository<CodeTemplate, Long> {

  /** 平台租户 ID：内置模板种子等平台级数据落在该租户。 */
  long PLATFORM_TENANT_ID = 0L;

  /**
   * 按租户分页。{@code CodeTemplate} 不是 {@code TenantAggregateRoot}，SDK 不会自动注入租户条件，
   * 故必须显式过滤，否则列表会跨租户返回全部模板。
   *
   * <p>口径：{@code tenant_id = :t OR (tenant_id = 0 AND created_by IS NULL)}——
   *
   * <ul>
   *   <li>当前租户自己的模板要查得到（隔离不是「什么都查不到」）
   *   <li>平台租户下的<strong>内置模板种子</strong>（无创建人）要查得到，否则模板选择页为空
   *   <li>但平台租户下若有用户自建的孤儿数据（{@code created_by} 非空），<strong>不得</strong>公开给所有租户
   * </ul>
   */
  default PageResult<CodeTemplate> findPageByTenant(long tenantId, int pageNo, int pageSize) {
    return QueryBuilder.from(CodeTemplate.class)
        .where(CodeTemplate::getTenantId)
        .eq(tenantId)
        .or(
            (Consumer<FluentQuery<CodeTemplate>>)
                g ->
                    g.where(CodeTemplate::getTenantId)
                        .eq(PLATFORM_TENANT_ID)
                        .and(CodeTemplate::getCreatedBy)
                        .isNull())
        .page(pageNo, pageSize);
  }

  /**
   * 租户可见口径的 SQL 片段（{@code static} 以便单测直接断言，无需数据库）。
   *
   * <p><b>为什么这里是字符串而不是 {@code Criteria}</b>：SDK 的 {@code Criteria.or(Consumer)} 会把 OR 组塞进 一个 {@code
   * fieldName == null} 的原生条件（{@code Criteria#addNativeCondition}），而 {@code
   * BaseRepository#validateCriteriaFields} 对每个条件无条件调用 {@code FieldCache.getFieldByName}， 取到 null 即抛
   * {@code UndefinedFieldException}。 它在 {@code toSql()} 层看起来完全正常——基于它写的单测会<b>假绿</b>，一到真实查询就炸。
   * 因此运行期一律走 {@code QueryBuilder}；在此缺陷修复前，不得改回 {@code pageByCriteria} + {@code or()}。
   */
  static String visibleToTenantSql() {
    return "tenant_id = :tenantId OR (tenant_id = "
        + PLATFORM_TENANT_ID
        + " AND created_by IS NULL)";
  }
}
