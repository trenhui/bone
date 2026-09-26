package com.bone.metadata.sdk.query.builder;

import com.bone.core.enums.Operator;
import com.bone.core.tenant.context.TenantContext;
import com.bone.core.util.ReflectionUtil;
import com.bone.metadata.sdk.domain.exception.MissingTenantContextException;
import com.bone.metadata.sdk.domain.model.ColumnMetadata;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一的可信租户过滤注入器（ADR-0029）。
 *
 * <p>核心不变量：<b>租户表查询必须被限定到单一租户</b>。取值优先级：
 *
 * <ol>
 *   <li>优先使用可信 {@link TenantContext}（请求线程由 JWT Filter 写入）；该路径下 caller 传入的 tenantId 被视为不可信，被忽略并
 *       WARN。
 *   <li>上下文为空时，若 caller 已在主表显式 {@code EQ} 限定到单一租户（跨租户 admin / 后台定时任务链路的既有行为），以 caller 值兜底——
 *       仍保持单租户隔离，不抛异常（向后兼容，且隔离未被破坏）。
 *   <li>两者皆无 → 失败关闭，抛 {@link MissingTenantContextException}：非单租户范围的查询打到租户表是真正的越权 / 静默空漏洞。
 * </ol>
 *
 * <p>逃生舱：{@link Criteria#isTenantFilterDisabled()} 为 true 时完全退出（不注入、不抛）。跨租户基础设施扫描（如 Outbox 中继）须显式
 * {@code disableTenantFilter()}。
 */
@Slf4j
public final class TenantFilterInjector {

  public static final String PARAM = "_sdk_tenant_id";
  private static final String TENANT_COL = "tenant_id";

  private TenantFilterInjector() {}

  /**
   * 向 WHERE 片段集合注入租户条件（SELECT / COUNT / ConditionalUpdate / DELETE 共用）。
   *
   * @param where WHERE 片段集合（会被追加一条租户条件）
   * @param params 命名参数集合（会被追加 {@link #PARAM}）
   * @param tbl 表元数据
   * @param criteria caller 传入的条件（逃生舱 / caller-EQ 兜底来源）；可为 null
   * @param useAlias 是否使用表别名 {@code m.}（SELECT/COUNT/ConditionalUpdate=true；DELETE=false）
   */
  public static void inject(
      List<String> where,
      Map<String, Object> params,
      TableMetadata tbl,
      Criteria<?> criteria,
      boolean useAlias) {
    if (!tbl.isTenantScoped()) {
      return;
    }
    if (criteria != null && criteria.isTenantFilterDisabled()) {
      log.warn(
          "Tenant filter DISABLED for table {} (cross-tenant scan); ensure platform:* authorization"
              + " + audit at application layer",
          tbl.getName());
      return;
    }

    // 1) 优先可信上下文
    Long ctxTenant = TenantContext.getTenantIdAsLong();
    if (ctxTenant != null) {
      addTenantClause(where, params, tbl, ctxTenant, useAlias);
      if (findCallerTenantEq(criteria) != null) {
        log.warn(
            "Caller-supplied tenantId EQ ignored; tenant resolved from TenantContext for table {}",
            tbl.getName());
      }
      return;
    }

    // 2) 上下文为空：允许 caller 已显式 EQ 限定到单一租户兜底（仍单租户隔离，不抛）
    Condition caller = findCallerTenantEq(criteria);
    if (caller != null && caller.getValues() != null && caller.getValues().length > 0) {
      Object v = caller.getValues()[0];
      if (v != null) {
        addTenantClause(where, params, tbl, normalizeTenantValue(v), useAlias);
        return;
      }
    }

    // 3) 既无上下文、caller 又未限定租户 → 失败关闭
    throw new MissingTenantContextException(tbl.getName());
  }

  /**
   * FluentQuery（DSL）通道的租户取值（ADR-0029 补口）：与 {@link #inject} 保持同一不变量——<b>租户表查询必须被限定到单一租户</b>。
   *
   * <p>优先级与 {@link #inject} 一致：可信 {@link TenantContext} &gt; caller 显式 EQ 兜底 &gt; 失败关闭。DSL 通道没有
   * {@link Criteria#isTenantFilterDisabled()} 逃生舱——跨租户基础设施扫描必须走 Criteria 通道（受架构门禁约束）。
   *
   * @param tbl 表元数据
   * @param callerTenantEq caller 在主表显式 EQ 限定的租户值（{@code tenantId} / {@code tenant_id}）；无则传 null
   * @return 注入用租户值；表非租户作用域时返回 null（调用方不注入）
   * @throws MissingTenantContextException 上下文与 caller 兜底皆无（租户表 + 非单租户范围 = 真越权 / 静默空漏洞）
   */
  public static Object resolveDslTenantValue(TableMetadata tbl, Object callerTenantEq) {
    if (!tbl.isTenantScoped()) {
      return null;
    }
    Long ctxTenant = TenantContext.getTenantIdAsLong();
    if (ctxTenant != null) {
      if (callerTenantEq != null) {
        log.warn(
            "Caller-supplied tenantId condition ignored; tenant resolved from TenantContext"
                + " (FluentQuery) for table {}",
            tbl.getName());
      }
      return ctxTenant;
    }
    if (callerTenantEq != null) {
      return normalizeTenantValue(callerTenantEq);
    }
    throw new MissingTenantContextException(tbl.getName());
  }

  /**
   * 解析 INSERT 时的租户值：优先使用可信 {@link TenantContext}（实体为 null 时补、与实体不符时以上下文为准并 WARN）， 上下文为 null
   * 时回退到实体字段（维持既有行为，不抛）。
   */
  public static Object resolveInsertTenantValue(TableMetadata tbl, Object entity) {
    if (!tbl.isTenantScoped()) {
      return null;
    }
    ColumnMetadata tc = tbl.getTenantIdColumn();
    Object entityVal = ReflectionUtil.getFieldValue(entity, tc.getFieldName());
    Long ctx = TenantContext.getTenantIdAsLong();
    if (ctx != null) {
      if (entityVal != null && !ctx.equals(entityVal)) {
        log.warn(
            "Entity tenantId {} overridden by TenantContext {} for insert into table {}",
            entityVal,
            ctx,
            tbl.getName());
      }
      return ctx;
    }
    return entityVal;
  }

  /** 找到 caller 在主表显式 EQ 限定的单一租户条件（物理列 tenant_id 或字段名 tenantId）。 */
  private static Condition findCallerTenantEq(Criteria<?> criteria) {
    if (criteria == null) {
      return null;
    }
    return criteria.getMainConditions().stream()
        .filter(c -> !c.isExtension())
        .filter(c -> c.getOperator() == Operator.EQ)
        .filter(c -> "tenantId".equals(c.getFieldName()) || TENANT_COL.equals(c.getColumn()))
        .findFirst()
        .orElse(null);
  }

  private static void addTenantClause(
      List<String> where,
      Map<String, Object> params,
      TableMetadata tbl,
      Object value,
      boolean useAlias) {
    where.add((useAlias ? "m." : "") + tbl.getTenantIdColumn().getName() + " = :" + PARAM);
    params.put(PARAM, value);
  }

  private static Object normalizeTenantValue(Object v) {
    if (v instanceof Number) {
      return ((Number) v).longValue();
    }
    if (v instanceof String) {
      try {
        return Long.parseLong((String) v);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("tenant_id value is not a valid long: " + v);
      }
    }
    return v;
  }
}
