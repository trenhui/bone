package com.bone.airag.config;

import com.bone.airag.application.RagAnswerService;
import com.bone.airag.application.RagApplicationService;
import com.bone.airag.domain.gateway.AnswerGenerationPort;
import com.bone.airag.domain.gateway.EmbeddingPort;
import com.bone.airag.domain.gateway.RagAclPort;
import com.bone.airag.domain.gateway.RagEventSink;
import com.bone.airag.domain.gateway.RerankPort;
import com.bone.airag.domain.gateway.TenantProvider;
import com.bone.airag.domain.repository.ChunkRepository;
import com.bone.airag.domain.service.DenyDocAcl;
import com.bone.airag.domain.service.GroundedPromptBuilder;
import com.bone.airag.domain.service.GroundingGuard;
import com.bone.airag.domain.service.NoDocAcl;
import com.bone.airag.domain.service.NoOpReranker;
import com.bone.airag.domain.service.SensitiveDataMasker;
import com.bone.airag.domain.service.UnavailableAnswerGeneration;
import com.bone.airag.infrastructure.embedding.HttpBgeEmbeddingClient;
import com.bone.airag.infrastructure.embedding.TransitionalHashEmbedding;
import com.bone.airag.infrastructure.event.LoggingRagEventSink;
import com.bone.airag.infrastructure.gateway.TenantContextProviderAdapter;
import com.bone.airag.infrastructure.rerank.HttpBgeRerankClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * bone-ai-rag 自动配置（Boot 3 {@code .imports} 机制）：注册租户解析 + 嵌入 / 重排序端口 + 文档级 ACL + 事件下沉 + 检索服务。
 *
 * <p>切片仓储由使用方通过 {@code @EnableSqlRepositories(basePackages="com.bone.airag.domain.repository")}
 * 注册；{@link RagApplicationService} 以 {@code @ConditionalOnBean(ChunkRepository)} 为前提，未接入持久化的上下文不装配。
 *
 * <p><b>推理服务条件装配</b>（2026-10-23 接线，修复「配置面死代码」）：配了 {@code bone.ai.rag.embedding.base-url} 才装配 {@link
 * HttpBgeEmbeddingClient}（共享 TEI / infinity，多副本友好、数据不出域），否则回落 {@link TransitionalHashEmbedding}；
 * {@code bone.ai.rag.rerank.base-url} 同理装配 {@link HttpBgeRerankClient}，否则回落 {@link NoOpReranker}。
 * {@link RagInferenceProperties} 经 {@code @EnableConfigurationProperties} 注册——缺此则整个 {@code
 * bone.ai.rag.*} 配置面无 bean 承载（运维配了也不生效）。
 *
 * <p><b>文档级 ACL 与事件下沉</b>（2026-10-23 RAG 诊断 P1 落地 seam；2026-10-24 企业复杂场景诊断 P0-2 改默认 fail-closed）：
 * {@link RagAclPort} 未接入时默认 {@link DenyDocAcl}（空可见集 = 拒绝检索，不放行全租户），接入方以 {@code @Bean} 覆盖为 文档中心 ACL
 * 实现后即生效（fail-closed）；开发联调可 {@code bone.ai.rag.acl.default-deny=false} 显式回落 {@link NoDocAcl}
 * （仅租户硬隔离，WARN 提示，禁止带入生产）。{@link RagEventSink} 默认 {@link LoggingRagEventSink}（结构化 WARN）， 目标态覆盖为
 * trace 落库 + 告警适配。
 */
@AutoConfiguration
@EnableConfigurationProperties(RagInferenceProperties.class)
public class RagAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(RagAutoConfiguration.class);

  /**
   * 租户解析（rag 侧端口）。
   *
   * <p>2026-10-21 更名（原 {@code tenantProvider}）：bone-ai 的 trace / gateway / rag 三个自动配置都注册了名为 {@code
   * tenantProvider} 的 Bean， 而 Spring Boot 3 默认禁止 Bean 定义覆盖（{@code
   * allow-bean-definition-overriding=false}）—— 三者同在一个 classpath（如 bone-ai-app 聚合）即抛 {@code
   * BeanDefinitionOverrideException}，整个上下文起不来。三者端口类型本就互不相同（按类型注入不受影响），此处按模块前缀唯一化。
   */
  @Bean
  @ConditionalOnMissingBean
  public TenantProvider ragTenantProvider() {
    return new TenantContextProviderAdapter();
  }

  /**
   * 嵌入端口：配了共享嵌入服务 base-url 则走 HTTP 客户端（bge-m3），否则回落过渡哈希（开发 / 无推理服务兜底）。
   *
   * <p>provider 故障不在本处降级——由检索链 catch 后回落 BM25 单路并 WARN（详设 §2.6 降级口径）。
   */
  @Bean
  @ConditionalOnMissingBean
  public EmbeddingPort embeddingPort(RagInferenceProperties props) {
    String baseUrl = props.getEmbedding().getBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      return new TransitionalHashEmbedding();
    }
    return new HttpBgeEmbeddingClient(
        baseUrl,
        props.getEmbedding().getModel(),
        props.getEmbedding().getVersion(),
        props.getEmbedding().getTimeout());
  }

  /**
   * 重排序端口：配了共享重排序服务 base-url 则走 HTTP 客户端（cross-encoder），否则回落 NoOp（保持 RRF 粗序）。
   *
   * <p>provider 故障由检索链 catch 后回落 NoOp 序并 WARN（详设 §2.6 降级口径）。
   */
  @Bean
  @ConditionalOnMissingBean
  public RerankPort rerankPort(RagInferenceProperties props) {
    String baseUrl = props.getRerank().getBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      return new NoOpReranker();
    }
    return new HttpBgeRerankClient(
        baseUrl, props.getRerank().getModel(), props.getRerank().getTimeout());
  }

  /**
   * 文档级 ACL（默认 fail-closed 拒绝检索；2026-10-24 企业复杂场景诊断 P0-2 改默认）。
   *
   * <p>未接入 ACL 实现时装配 {@link DenyDocAcl}（空可见集 = 拒绝检索）而非 {@link NoDocAcl}（放行全租户）——企业单租户内 多部门 /
   * 多团队场景下，兜底放行即跨文档越权面。接入方以 {@code @Bean} 覆盖（读文档中心 / bone-ai-document ACL）后， 「无 ACL 归属的 doc 不可见」即
   * fail-closed 生效（403 {@code AI_RAG_DOC_FORBIDDEN}）。
   *
   * <p>开发联调逃生口：{@code bone.ai.rag.acl.default-deny=false} 显式回落 {@link NoDocAcl}（仅租户硬隔离）， 打 WARN——
   * 该形态存在同租户跨文档越权面，禁止带入生产。
   */
  @Bean
  @ConditionalOnMissingBean
  public RagAclPort ragDocAcl(RagInferenceProperties props) {
    if (props.getAcl().isDefaultDeny()) {
      log.warn(
          "RagAclPort 未接入：默认拒绝检索（fail-closed，DenyDocAcl 空可见集）。生产必须以 @Bean 提供文档中心 ACL 实现；"
              + "开发联调可显式 bone.ai.rag.acl.default-deny=false 回落 NoDocAcl（仅租户硬隔离，禁止带入生产）");
      return new DenyDocAcl();
    }
    log.warn(
        "RagAclPort 未接入且 bone.ai.rag.acl.default-deny=false：回落 NoDocAcl（仅租户硬隔离）——"
            + "同租户内跨文档越权面存在，仅限开发联调，禁止带入生产");
    return new NoDocAcl();
  }

  /**
   * 检索事件下沉（默认结构化 WARN 日志）：接入方以 {@code @Bean} 覆盖为 bone-ai-trace 落库 + 告警适配， 检索链零改动（详设 §2.6 P1-10）。
   */
  @Bean
  @ConditionalOnMissingBean
  public RagEventSink ragEventSink() {
    return new LoggingRagEventSink();
  }

  /**
   * 生成侧接地守卫（「无出处不得生成」的运行时校验）。
   *
   * <p>默认实现是确定性词面校验（{@link GroundingGuard}），零外部依赖；生成侧（bone-ai-app / 接 R1 后的 gateway） 注入后对 LLM 输出调用
   * {@code verify(answer, contexts)}，判定不通过即拒答或降级为「仅返回原文片段」。
   *
   * <p>不在此处硬接生成链：bone-ai-gateway 当前不依赖 rag（唯一消费方是 bone-ai-app 的 RagController）， 生成侧接线随
   * R1（检索锚点传入生成）一并完成，本 Bean 就是那条接线的落点。
   */
  @Bean
  @ConditionalOnMissingBean
  public GroundingGuard groundingGuard(RagInferenceProperties props) {
    return new GroundingGuard(
        props.getGrounding().getMinSupportRatio(), props.getGrounding().getMaxUnsupportedRatio());
  }

  @Bean
  @ConditionalOnMissingBean
  public GroundedPromptBuilder groundedPromptBuilder(RagInferenceProperties props) {
    return new GroundedPromptBuilder(props.getContextTokenBudget());
  }

  /**
   * 答案生成端口（默认显式不可用）：接入方在组合根以 {@code @Bean AnswerGenerationPort} 覆盖为网关适配器。
   *
   * <p>默认实现抛 {@code AI_RAG_ANSWER_GENERATION_UNAVAILABLE} 而非返回空串——「忘了接生成」必须在首次调用就暴露，
   * 不能静默变成「模型答了但没内容」。适配示例见 {@link AnswerGenerationPort} 注释。
   */
  @Bean
  @ConditionalOnMissingBean
  public AnswerGenerationPort answerGenerationPort() {
    return new UnavailableAnswerGeneration();
  }

  /**
   * RAG 回答闭环（检索 → 接地提示词 → 生成 → 接地校验）。
   *
   * <p>依赖 {@code ragService} 存在（即已接入切片仓储）；未接入持久化的上下文不装配本闭环。
   */
  @Bean
  @ConditionalOnBean(name = "ragService")
  @ConditionalOnMissingBean
  public RagAnswerService ragAnswerService(
      RagApplicationService ragService,
      AnswerGenerationPort answerGenerationPort,
      GroundedPromptBuilder groundedPromptBuilder) {
    return new RagAnswerService(ragService, answerGenerationPort, groundedPromptBuilder);
  }

  @Bean
  @ConditionalOnBean(name = "chunkRepository") // 按名而非按类型：SDK 仓储是 FactoryBean 代理，类型不可静态推断
  @ConditionalOnMissingBean
  public RagApplicationService ragService(
      ChunkRepository chunkRepository,
      EmbeddingPort embeddingPort,
      RerankPort rerankPort,
      TenantProvider tenantProvider,
      RagAclPort ragDocAcl,
      RagEventSink ragEventSink,
      GroundingGuard groundingGuard,
      RagInferenceProperties props) {
    return new RagApplicationService(
        chunkRepository,
        embeddingPort,
        rerankPort,
        tenantProvider,
        ragDocAcl,
        ragEventSink,
        props.getCandidatePool(),
        props.getMmrLambda(),
        props.getWarnScanThreshold(),
        props.getSlowRetrieveMs(),
        groundingGuard,
        props.getIngest().isPiiMaskEnabled()
            ? new SensitiveDataMasker()
            : SensitiveDataMasker.disabled());
  }
}
