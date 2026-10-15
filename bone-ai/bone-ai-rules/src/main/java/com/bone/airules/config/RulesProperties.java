package com.bone.airules.config;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** bone-ai-rules 配置（{@code bone.ai.rules.*}）。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bone.ai.rules")
public class RulesProperties {

  /** 单条规则求值超时（毫秒）。 */
  private long timeoutMillis = 2000;

  /** 场景 → 规则定义（批一内存驱动）。 */
  private Map<String, RuleDefinition> rules = new LinkedHashMap<>();

  @Getter
  @Setter
  public static class RuleDefinition {
    private String expression;
    private String version = "v1";
  }
}
