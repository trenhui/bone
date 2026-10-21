package com.bone.ai.gateway.infrastructure.routing;

import com.bone.ai.gateway.domain.gateway.ModelRouter;
import java.util.Map;

/**
 * 配置驱动的模型档位路由：{@code bone.ai.gateway.routing.<scenario>=<model>} 把场景映射到模型档位。
 *
 * <p>无映射 / 空场景回落 {@code requestedModel}（不改变既有行为）；映射命中即用配置模型覆盖调用方的请求模型—— 把「简单场景走廉价模型」变成改配置而非改代码（成本红线
 * / 降档）。
 */
public class ConfigDrivenModelRouter implements ModelRouter {

  private final Map<String, String> scenarioToModel;

  public ConfigDrivenModelRouter(Map<String, String> scenarioToModel) {
    this.scenarioToModel = scenarioToModel == null ? Map.of() : Map.copyOf(scenarioToModel);
  }

  @Override
  public String resolveModel(String scenario, String requestedModel) {
    if (scenario == null || scenario.isBlank()) {
      return requestedModel;
    }
    return scenarioToModel.getOrDefault(scenario, requestedModel);
  }

  /** 是否配置了该场景的路由（供网关判定是否记录路由决策日志）。 */
  public boolean hasRoute(String scenario) {
    return scenario != null && scenarioToModel.containsKey(scenario);
  }
}
