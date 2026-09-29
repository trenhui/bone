package com.bone.studio.generator.domain.gateway;

/**
 * 当前租户取值端口。
 *
 * <p>为什么要有它：本模块此前<strong>全模块零处读取租户上下文</strong>——{@code WebTenantConfiguration} 只负责把请求头写进 {@code
 * TenantContext}，业务侧要么由调用方从请求参数带 {@code tenantId}，要么在缺失时回落硬编码魔法值（{@code DataSource} 落 {@code
 * 0L}、{@code CatalogMetadataGatewayAdapter} 用 {@code 1L}）。后果是新建的数据源被写到平台租户 0，叠加实体已声明 {@code
 * tenantId} 后该租户永远读不到自己建的数据； 查询侧则恒按 {@code 1L} 过滤，表现为空结果或跨租户读取，取决于 SDK 是否叠加注入——两者都是缺陷，只是严重度不同。
 *
 * <p>把「租户从哪来」收敛到一个接口后：① 用例不再各写各的默认值；② 异步线程丢失上下文时表现为<em>显式失败</em>而非「静默变成平台数据」； ③ 定时任务 /
 * 测试可用桩替换实现，不必改用例。
 */
public interface TenantProvider {

  /**
   * 当前租户；无法确定时返回 {@code null}。
   *
   * <p>返回 {@code null} 不是「可以用 0 兜底」的意思——调用方必须按平台语义<strong>失败关闭</strong>（抛 {@code
   * GEN_TENANT_CONTEXT_MISSING}），禁止再回落到任何魔法默认值。
   */
  Long currentTenantIdOrNull();
}
