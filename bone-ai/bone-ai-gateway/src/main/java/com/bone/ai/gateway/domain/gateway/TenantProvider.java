package com.bone.ai.gateway.domain.gateway;

/**
 * 租户解析端口（网关计量用）：把「当前租户」从基础设施（TenantContext）注入网关， 使 application 层不直连租户上下文
 * （ArchUnit：businessLayersMustNotReadTenantContextDirectly）。
 *
 * <p>与 bone-ai-trace / bone-ai-rag 的同名端口同型——各模块独立定义、由使用方覆盖，库形态零耦合。 网关本身保持租户无关：调用链路不因租户缺失
 * 而失败，计量事件的租户字段在缺失时为空（POC 单租户口径，详设 §2.1）。
 */
public interface TenantProvider {

  /** 当前租户（上下文未设置时返回 null，POC 单租户口径）。 */
  String currentTenantId();
}
