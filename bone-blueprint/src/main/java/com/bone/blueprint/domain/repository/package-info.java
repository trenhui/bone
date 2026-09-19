/**
 * domain 层仓储端口：聚合根的持久化接口，<strong>同时承载本聚合的读模型</strong>（ADR-0030 合并形态）。
 *
 * <p>所有仓储继承 {@code bone-metadata-sdk} 的 {@link com.bone.metadata.sdk.Repository}， 由
 * {@code @EnableSqlRepositories} 注解在启动类上代理实现，不需要手动写仓储实现类。 这是平台唯一的持久化方案，禁止引入 MyBatis / JPA /
 * Hibernate / MyBatis-Plus 等其他 ORM。
 *
 * <p><b>能放什么</b>：写（{@code save} / {@code update}，经 SDK 代理）、聚合加载（{@code
 * findByIdInTenant}）、以及<strong>本聚合</strong>的读模型方法 ——返回域层投影（{@code
 * domain/{ctx}/{aggregate}/projection}）、列表分页、标量计数。读方法必须是 {@code default}（有方法体）或带外置 SQL 模板，
 * 不允许裸抽象方法（由模块级治理测试 {@code SqlTemplateGovernanceTest} 门禁①拦截）。
 *
 * <p><b>不能放什么</b>：跨聚合读、报表、搜索、多聚合组合——这些走 {@code application/query/port/*QueryPort}；JOIN
 * 越出本聚合表集合的读同样不可 （门禁⑥）。本模块当前<strong>没有</strong> {@code QueryPort}
 * 实例：订单与支付的读都是本聚合读，已全部并入域仓储，这不是「读侧缺能力」而是 「没有读模型分歧就不引入端口」（ADR-0028 的判据，见 {@code PaymentRepository}
 * 类注释）。
 *
 * <p><b>全租户运维入口只在此声明</b>：扫描方法名必须以 {@code AllTenants} 结尾，并显式关闭租户过滤（Criteria 的 {@code
 * disableTenantFilter()} 或 SQL 的 {@code @TenantScope(ALL)}），调用方只允许 {@code
 * adapter.schedule}（E-2）。这个后缀同时是 {@code all_tenants_scan_only_by_schedule} 的识别判据与本模块 {@code
 * ArchitectureTest} 的约束对象，改名会静默关闭这层防护。
 */
package com.bone.blueprint.domain.repository;
