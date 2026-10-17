package com.bone.aiapp.config;

import com.bone.aiintegration.domain.model.IntegrationMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** bone-ai-app 外部化配置（业界最佳实践：配置与代码分离，运行时由环境变量/配置中心注入）。 */
@Component
@ConfigurationProperties(prefix = "bone.ai")
@Getter
@Setter
public class BoneAiProperties {

  /** 受控检索默认返回条数。 */
  private int ragTopK = 5;

  /** 集成节点默认三态模式（AUTOMATED=全自动 / ASSISTED=待人工确认 / SHADOW=仅观测不落地）。 */
  private IntegrationMode defaultIntegrationMode = IntegrationMode.AUTOMATED;

  /** 消警外发默认 webhook 地址（生产由配置中心注入；为空则外发失败并降级日志）。 */
  private String notificationWebhookUrl = "";

  /** 评测裁判（LLM-as-Judge）模型档位（经网关门面调用，计量 / 证据链同普通 LLM 调用）。 */
  private String judgeModel = "qwen-judge";
}
