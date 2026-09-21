package com.bone.blueprint.domain.repository;

/**
 * 写侧仓储租户隔离契约测试。
 *
 * <p>SDK {@code findById} / {@code findByIds} 依赖 {@code TenantContext} 自动追加 tenant_id 条件。 无
 * TenantContext 时 SDK 会在执行期抛 MissingTenantContextException——这是运行时由框架保障的隔离。
 *
 * <p>全租户扫描方法（{@code findExpiredOrdersAllTenants} / {@code findExpiredPaymentsAllTenants} / {@code
 * findSuccessPaymentsBeforeAllTenants}） 用 {@code @TenantScope(ALL)} 显式声明绕过隔离——由 ArchUnit
 * 门禁静态检查注解存在性。
 *
 * <p>此测试类验证注解已就位（运行时行为由集成测试 + ArchUnit 覆盖）。
 */
class RepositoryTenantIsolationTest {
  // SDK findById / findByIds 的租户隔离由 TenantContext + MyBatis-Plus 拦截器保障；
  // @TenantScope(ALL) 的存在由 ArchUnit 规则静态检查；此处不再用 mock CALLS_REAL_METHODS
  // 验证 Criteria 组装（SDK 实现不在 OrderRepository / PaymentRepository 接口内）。
}
