package com.bone.metadata.sdk.domain.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 {@code @Sql} 仓储方法上，声明该方法的多租户处理策略。
 *
 * <p><b>为什么需要显式标注</b>：{@code @Sql} 通道历史上完全不经过 {@code TenantFilterInjector}（见 ADR-0029），默认不注入租户。
 * 为避免"作者以为自动、实则裸奔"的跨租户隐患，AUTO 必须显式声明，而非默认开启。
 *
 * <p><b>AUTO 的注入锚点</b>：默认对"单表 + 可选 WHERE、无 JOIN/子查询"的简单查询做启发式注入； 联表或复杂查询请在 SQL 中放置锚点标记 {@code
 * /*bone:tenant*}{@code /}，SDK 会把它替换为 {@code <column> = :__boneTenantId__}。锚点写法示例：
 *
 * <pre>{@code
 * SELECT ... FROM t_order o LEFT JOIN t_order_item oi ...
 * WHERE o.id = :orderId /*bone:tenant*\/ AND o.deleted = 0
 * }</pre>
 *
 * <p><b>缺上下文即失败关闭</b>：AUTO 模式下若 {@code TenantContext} 无租户，抛 {@link
 * com.bone.metadata.sdk.domain.exception.MissingTenantContextException}， 避免退化为跨租户读取。
 *
 * <p><b>必须显式声明</b>：SQL 通道方法（{@code @Sql} 注解或外置 {@code .sql} 模板）**必须**在方法上、或在其仓储接口上 声明本注解，否则 {@code
 * RepositoryFactoryBean} 在启动期直接拒绝注册该仓储（{@link IllegalStateException}）。 这条校验的理由是默认值本身：{@link
 * TenantScopeMode#MANUAL} 表示"不注入"， 因此"忘了写"的后果是静默跨租户读写而不是报错——由启动期拦截把静默失败变成显式失败。
 *
 * <p><b>接口级默认值</b>：注解可以放在仓储接口上，作为该接口所有 SQL 方法的默认策略（方法级注解优先）。 对整仓同构的接口（如全部走 MANUAL 的复杂报表仓），
 * 用接口级注解一次声明即可，不必逐方法标注。注意接口级默认值不向父接口继承查找。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface TenantScope {

  /** 作用模式，默认 {@link TenantScopeMode#MANUAL}（向后兼容，不注入）。 */
  TenantScopeMode value() default TenantScopeMode.MANUAL;

  /** 租户列表达式。联表查询须带别名（如 {@code "o.tenant_id"}），单表可省略（默认 {@code "tenant_id"}）。 */
  String column() default "tenant_id";

  /** 是否在租户条件后自动追加软删条件 {@code AND deleted = 0}（仅限租户表）。 */
  boolean softDelete() default false;
}
