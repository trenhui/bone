package com.bone.studio.generator.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import java.util.List;

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
  /**
   * 仅返回当前租户<strong>自己</strong>的模板（单租户隔离，不动用跨租户逃生舱）。 平台级内置模板种子对所有租户可见的逻辑在 {@link
   * #findPlatformTemplatesAllTenants} 中受控读取， 再由应用服务（已登记的合法跨租户调用方）合并，见 {@link
   * com.bone.studio.generator.application.GetCodeTemplateListQueryApplicationService}。
   */
  default PageResult<CodeTemplate> findPageByTenant(long tenantId, int pageNo, int pageSize) {
    return QueryBuilder.from(CodeTemplate.class)
        .where(CodeTemplate::getTenantId)
        .eq(tenantId)
        .page(pageNo, pageSize);
  }

  /**
   * 全租户入口（ADR-0029/ADR-0030，命名后缀 {@code AllTenants} 见 E-4.4/E-13.3）：受控读取<strong>平台级内置模板种子</strong>
   * （tenant_id = 0 且 created_by 为空），供所有租户在模板选择页看到内置模板。
   *
   * <p><b>为何是跨租户入口</b>：gen_code_template 是租户作用域表，SDK 的 TenantFilterInjector 会强制注入 "AND tenant_id =
   * 当前租户"，平台(0)种子对非平台租户不可见；故必须显式 {@code disableTenantFilter()} 并严格限定为 (tenant_id = 0 AND created_by
   * IS NULL) —— 只暴露「无创建人的平台种子」，绝不暴露其他租户数据。
   *
   * <p>调用方已在模块的 ArchitectureTest 中登记为合法跨租户调用方（仅此只读场景），不向其它请求侧暴露。
   */
  default PageResult<CodeTemplate> findPlatformTemplatesAllTenants(int pageNo, int pageSize) {
    Criteria<CodeTemplate> criteria =
        Criteria.<CodeTemplate>builder()
            .entityClass(CodeTemplate.class)
            .disableTenantFilter()
            .eq(CodeTemplate::getTenantId, PLATFORM_TENANT_ID)
            .isNull(CodeTemplate::getCreatedBy);
    return this.pageByCriteria(criteria);
  }

  /**
   * 全租户入口（命名后缀 {@code AllTenants}）：平台(0)租户下<strong>全部已发布</strong>模板，不限创建人。
   *
   * <p><b>为何与 {@link #findPlatformTemplatesAllTenants} 口径不同</b>：种子口径（{@code created_by IS NULL}）
   * 用于「对外可见性」；但迁移 0015 收敛的内建模板一部分带创建人（非种子），若按种子口径做 「不选模板 = 全部内建」的默认解析，会漏掉一半内建模板（create/update
   * 命令、DTO、装配器等）， 生成的代码包残缺。默认生成读取的是模板内容（与用户显式勾选 {@link #findByIdAllTenants} 同级暴露）， 按 {@code
   * tenant_id = 0 AND status = PUBLISHED} 取全部内建是正确口径。
   */
  default PageResult<CodeTemplate> findPlatformPublishedAllTenants(int pageNo, int pageSize) {
    Criteria<CodeTemplate> criteria =
        Criteria.<CodeTemplate>builder()
            .entityClass(CodeTemplate.class)
            .disableTenantFilter()
            .eq(CodeTemplate::getTenantId, PLATFORM_TENANT_ID)
            .eq(CodeTemplate::getStatus, "PUBLISHED");
    return this.pageByCriteria(criteria);
  }

  /**
   * 全租户入口（命名后缀 {@code AllTenants}）：按 id 读取模板，绕过租户隔离。
   *
   * <p>用途：代码生成时校验用户所选模板是否真实存在——用户从「对自己可见」的列表里选了平台(0)种子模板， 但若按当前租户查库会查不到，故需受控跨租户读取单个模板（仍只命中那一行的
   * id，不外溢到其他租户数据）。 调用方（校验服务）已在模块 ArchitectureTest 中登记为合法跨租户调用方。
   */
  default CodeTemplate findByIdAllTenants(Long id) {
    Criteria<CodeTemplate> criteria =
        Criteria.<CodeTemplate>builder()
            .entityClass(CodeTemplate.class)
            .disableTenantFilter()
            .eq(CodeTemplate::getId, id);
    List<CodeTemplate> list = this.pageByCriteria(criteria).getRecords();
    return list.isEmpty() ? null : list.get(0);
  }

  /**
   * 租户可见口径的 SQL 片段（{@code static} 以便单测直接断言，无需数据库）。
   *
   * <p><b>为什么这里是字符串而不是真实查询</b>：口径是文档化的—— {@code tenant_id = :tenantId OR (tenant_id = 0 AND
   * created_by IS NULL)}。但在运行期，SDK 的 {@code TenantFilterInjector}（ADR-0029）会强制给租户作用域表注入 {@code AND
   * tenant_id = 当前租户}， 且 DSL/QueryBuilder 通道没有逃生舱，单纯用 OR 或 DSL 都拿不到平台(0)模板；而 {@code
   * Criteria.or(Consumer)} 又会把 OR 组塞进 {@code fieldName == null} 的原生条件，被 {@code
   * validateCriteriaFields} 判 {@code UndefinedFieldException}（单测假绿、运行期才炸）。 因此运行期改用「QueryBuilder
   * 取本租户 + Criteria.disableTenantFilter() 严格限定 {@code (tenant_id=0 AND created_by IS NULL)}
   * 取平台种子」两段查询（见 {@link #findPageByTenant}）。此字符串仅作口径契约断言，不得据此直接拼 SQL。
   */
  static String visibleToTenantSql() {
    return "tenant_id = :tenantId OR (tenant_id = "
        + PLATFORM_TENANT_ID
        + " AND created_by IS NULL)";
  }
}
