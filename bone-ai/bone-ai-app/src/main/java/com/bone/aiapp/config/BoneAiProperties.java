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

  /** 受控检索默认分数阈值（精排后过滤；0 = 不启用，量纲双轨见 RagQuery，须按实测分布校准）。 */
  private double ragMinScore = 0.0;

  /**
   * 受控检索默认容量护栏（作用域切片数上限，超限 fail-closed 抛 AI_RAG_SCOPE_OVER_LIMIT）。
   *
   * <p>2026-10-24 由 10000 下调至 3000（企业复杂场景诊断 P1-1）：过渡态全量载入内存打分，几千条时延迟已悄然劣化而 10000
   * 是「不可行点」才触发——正常水位下已慢无告警。3000 取实测安全水位 2000~5000 中偏上，与 {@code warn-scan-threshold} 2000
   * 形成「先预警、再拒绝」两级；终局 pgvector + ANN 索引化后此阈值即迁移触发信号。
   */
  private int ragMaxScan = 3_000;

  /** 集成节点默认三态模式（AUTOMATED=全自动 / ASSISTED=待人工确认 / SHADOW=仅观测不落地）。 */
  private IntegrationMode defaultIntegrationMode = IntegrationMode.AUTOMATED;

  /** 消警外发默认 webhook 地址（生产由配置中心注入；为空则外发失败并降级日志）。 */
  private String notificationWebhookUrl = "";

  /** 评测裁判（LLM-as-Judge）模型档位（经网关门面调用，计量 / 证据链同普通 LLM 调用）。 */
  private String judgeModel = "qwen-judge";

  /**
   * RAG 回答链生成模型档位（经网关门面调用，计量 / 证据链 / 治理链同普通 LLM 调用）。
   *
   * <p>R1 接线（2026-10-24）：rag 回答链的「G」经组合根 {@code RagAnswerGenerationAdapter} 转调网关，模型档位由此配置决定——
   * 生产按实际部署的模型覆盖（{@code bone.ai.rag-answer-model}），缺省与网关门面默认档一致；引用接地质量对模型档位敏感，建议按评测集实测后上调。
   */
  private String ragAnswerModel = "gpt-4o-mini";
}
