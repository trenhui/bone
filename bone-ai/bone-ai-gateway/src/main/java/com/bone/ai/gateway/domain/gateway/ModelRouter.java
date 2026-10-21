package com.bone.ai.gateway.domain.gateway;

/**
 * 模型档位路由端口（scenario → 模型，成本红线 / 降档的核心，详设 §2.1 演进项）。
 *
 * <p>调用方在 {@code LlmRequest.scenario} 声明业务场景，网关经本端口把场景映射到具体模型档位——简单场景（如条款问答）走廉价模型、
 * 复杂场景（如核赔判定）走强模型，把「该用多贵的模型」从调用方硬编码收敛为平台可配置策略。 级联升档 / 失败降备援可在实现内扩展（同端口）。
 *
 * <p>默认 {@code PassthroughModelRouter}（不路由，按请求原样模型调用）；接入方以 {@code @Bean} 覆盖为配置驱动 / 动态策略实现。
 */
@FunctionalInterface
public interface ModelRouter {

  /**
   * 解析场景应使用的模型。
   *
   * @param scenario 业务场景（可为空 = 不参与路由）
   * @param requestedModel 调用方请求的模型（路由缺省 / 无映射时回落）
   * @return 生效模型（不得为 null；无映射时返回 {@code requestedModel}）
   */
  String resolveModel(String scenario, String requestedModel);
}
