package com.bone.ai.gateway.domain.service;

import com.bone.ai.gateway.domain.gateway.ModelRouter;

/** 默认路由（透传）：不按场景改模型，按调用方请求的模型原样调用（无路由配置时的安全默认）。 */
public final class PassthroughModelRouter implements ModelRouter {

  public static final PassthroughModelRouter INSTANCE = new PassthroughModelRouter();

  private PassthroughModelRouter() {}

  @Override
  public String resolveModel(String scenario, String requestedModel) {
    return requestedModel;
  }
}
