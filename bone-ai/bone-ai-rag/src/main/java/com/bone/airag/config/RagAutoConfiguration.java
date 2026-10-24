package com.bone.airag.config;

import com.bone.airag.application.RagApplicationService;
import com.bone.airag.domain.gateway.EmbeddingPort;
import com.bone.airag.domain.gateway.RagAclPort;
import com.bone.airag.domain.gateway.RagEventSink;
import com.bone.airag.domain.gateway.RerankPort;
import com.bone.airag.domain.gateway.TenantProvider;
import com.bone.airag.domain.repository.ChunkRepository;
import com.bone.airag.domain.service.NoDocAcl;
import com.bone.airag.domain.service.NoOpReranker;
import com.bone.airag.infrastructure.embedding.HttpBgeEmbeddingClient;
import com.bone.airag.infrastructure.embedding.TransitionalHashEmbedding;
import com.bone.airag.infrastructure.event.LoggingRagEventSink;
import com.bone.airag.infrastructure.gateway.TenantContextProviderAdapter;
import com.bone.airag.infrastructure.rerank.HttpBgeRerankClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * bone-ai-rag 自动配置（Boot 3 {@code .imports} 机制）：注册租户解析 + 嵌入 / 重排序端口 + 文档级 ACL +
 * 事件下沉 + 检索服务。
 *
 * <p>切片仓储由使用方通过 {@code @EnableSqlRepositories(basePackages="com.bone.airag.domain.repository")}
 * 注册；{@link RagApplicationService} 以 {@code @ConditionalOnBean(ChunkRepository)} 为前提，未接入持久化的上下文不装配。
 *
 * <p><b>推理服务条件装配</b>（2026-10-23 接线，修复「配置面死代码」）：配了 {@code bone.ai.rag.embedding.base-url} 才装配
 * {@link HttpBgeEmbeddingClient}（共享 TEI / infinity，多副本友好、数据不出域），否则回落 {@link TransitionalHashEmbedding}；
 * {@code bone.ai.rag.rerank.base-url} 同理装配 {@link HttpBgeRerankClient}，否则回落 {@link NoOpReranker}。
 * {@link RagInferenceProperties} 经 {@code @EnableConfigurationProperties} 注册——缺此则整个 {@code bone.ai.rag.*} 配置面无
 * bean 承载（运维配了也不生效）。
 *
 * <p><b>文档级 ACL 与事件下沉</b>（2026-10-23 RAG 诊断 P1 落地）：{@link RagAclPort} 默认 {@link NoDocAcl}
 * （仅租户硬隔离兜底），接入方以 {@code @Bean} 覆盖为文档中心 ACL 实现后即生效（fail-closed）；
 * {@link RagEventSink} 默认 {@link LoggingRagEventSink}（结构化 WARN），目标态覆盖为 trace 落库 + 告警适配。
 */
@AutoConfiguration
@EnableConfigurationProperties(RagInferenceProperties.class)
public class RagAutoConfiguration {

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
   * 文档级 ACL（默认仅租户硬隔离兜底）：接入方以 {@code @Bean} 覆盖（读文档中心 / bone-ai-document ACL），
   * 覆盖后「无 ACL 归属的 doc 不可见」即 fail-closed 生效（详设 §2.6 P1-7）。
   */
  @Bean
  @ConditionalOnMissingBean
  public RagAclPort ragDocAcl() {
    return new NoDocAcl();
  }

  /**
   * 检索事件下沉（默认结构化 WARN 日志）：接入方以 {@code @Bean} 覆盖为 bone-ai-trace 落库 + 告警适配，
   * 检索链零改动（详设 §2.6 P1-10）。
   */
  @Bean
  @ConditionalOnMissingBean
  public RagEventSink ragEventSink() {
    return new LoggingRagEventSink();
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
        props.getSlowRetrieveMs());
  }
}
