package com.bone.airag.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 推理服务配置（{@code bone.ai.rag.*}）。
 *
 * <p>嵌入 / 重排序默认走<b>共享推理服务</b>（TEI / infinity 兼容，多副本友好，避免每副本加载 GB 级模型）。 未配置 {@code base-url}
 * 时分别回退：嵌入 → 过渡哈希（无语义，仅保链路可跑）、重排序 → {@code NoOpReranker} （保持 RRF 序）。进程内 ONNX（onnxruntime + 模型文件）作为
 * L4 部署备选，经同一 {@code EmbeddingPort} / {@code RerankPort} 端口由消费方以 {@code @Bean} 覆盖接入。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bone.ai.rag")
public class RagInferenceProperties {

  private Embedding embedding = new Embedding();
  private Rerank rerank = new Rerank();
  private Grounding grounding = new Grounding();
  private Acl acl = new Acl();
  private Ingest ingest = new Ingest();

  /** 进入重排序的候选池大小（≥ topK）：RRF 融合后取前 N 名送 cross-encoder 精排，默认 30（详设 §2.6 top-30）。 */
  private int candidatePool = 30;

  /**
   * 分数阈值（精排后按最终分过滤，默认 0 = 不启用）。
   *
   * <p>量纲双轨：接入 cross-encoder 时为 rerank 分（按实测分布校准）；未接入时为 RRF 融合分（~0.01 量级）。
   * 阈值须按评测集回流数据标定，默认关闭避免误杀（详设 §2.6）。
   */
  private double minScore = 0.0;

  /**
   * 容量护栏（fail-closed）：单作用域切片数上限，超限抛 {@code AI_RAG_SCOPE_OVER_LIMIT} 而非静默截断， 默认 3000（2026-10-24 由
   * 10000 下调：过渡态全量载入内存打分，几千条时延迟已悄然劣化， 10000 是「不可行点」才触发、正常水位下已慢无告警；实测安全水位 2000~5000 取中偏上，与 {@code
   * warnScanThreshold} 2000 形成「先预警、再拒绝」两级。终局索引化（R4 pgvector + ANN）后此阈值即迁移触发信号）。
   */
  private int maxScan = 3_000;

  /**
   * MMR 多样性权重 λ（top-K 选取：{@code λ·相关性 − (1−λ)·与已选的最大相似度}，防 top-K 同质化；默认 0 = 关闭）。
   *
   * <p>λ 约定：λ∈(0,1) 生效；λ=1 纯相关性（数学退化，等价关闭）；λ=0 按约定视为关闭而非数学上的纯多样性 ——避免运维误配 0
   * 得到乱序结果。阈值须按评测集回流数据校准（批二），默认关闭避免误伤（详设 §2.6 P1-9）。
   */
  private double mmrLambda = 0.0;

  /**
   * 容量预警阈值（切片数）：作用域切片数超过此值（且未触 {@code maxScan}）即发 {@code SCOPE_NEAR_LIMIT} 事件。
   *
   * <p>过渡态每次检索全量载入内存打分，切片数到几千量级延迟已悄然劣化却无信号。默认 2000 作为「撞上限前的可观测预警」 ——引导运维下调 {@code max-scan} 或推进
   * R4（pgvector + ANN 向量索引化）。<=0 关闭预警。
   */
  private int warnScanThreshold = 2_000;

  /**
   * 慢检索阈值（毫秒）：单次 retrieve 墙钟超过此值即发 {@code RETRIEVE_SLOW} 事件，供落 trace / 接 P99 告警。
   *
   * <p>规模瓶颈的抓手——全量内存 BM25 + 余弦下切片数增长会线性推高延迟。默认 500ms 作首结果/单查询延迟经验水位， 运维可按实测 P99 校准。设为 0
   * 则任意正耗时都告警（仅测试用）。
   */
  private long slowRetrieveMs = 500;

  /**
   * 阶段级检索链路计时（B16 / R6 子项，opt-in，默认关）：开启时每次 retrieve 末尾汇总各阶段墙钟时延事件 （{@code RETRIEVE_STAGES}，形如
   * {@code acl=..;embed=..;score=..;rerank=..;mmr=..;assemble=..;total=..;candidates=..}）， 经
   * RagEventSink 落 trace 证据快照，供诊断 / 压测期定位性能瓶颈（与 {@code RETRIEVE_SLOW} 同告警线）。
   *
   * <p>默认关闭——每查询多落一条证据快照，生产常态不付该开销；待接入 OTel 后升级为真实 gen_ai.* 子 span。
   */
  private boolean stageTracingEnabled = false;

  /**
   * 上下文 token 预算（{@code bone.ai.rag.context-token-budget}）：进入提示词的上下文总量上限，超出按最终分从高到低截断。
   *
   * <p>默认 0 = 不限制（由调用方/编排层兜底）。它与网关 LLM {@code maxTokens}、系统提示词预留共同构成端到端上下文预算
   * ——只配那一侧，另一侧无界增长时仍会超窗。截断不是静默行为：一旦发生，回答结果里带 {@code CONTEXT_TRUNCATED} 降级痕迹，让「答不全」可归因。
   */
  private int contextTokenBudget = 0;

  @Getter
  @Setter
  public static class Embedding {
    /** 共享嵌入服务 base-url（如 {@code http://tei:80}）；空则回退过渡哈希实现。 */
    private String baseUrl;

    private String model = "BAAI/bge-m3";

    /** 嵌入模型版本标识，写入 chunk.embedding_ver；与之不符的旧向量 fail-closed 跳过向量路。 */
    private String version = "bge-m3@http";

    private Duration timeout = Duration.ofSeconds(10);
  }

  /**
   * 生成侧接地守卫阈值（{@code bone.ai.rag.grounding.*}）。
   *
   * <p>两个阈值都只决定「判多严」，不决定「是否校验」——守卫本身是 fail-closed 的，接入方注入即生效， 不存在「配置漏了就不校验」的静默通道。
   */
  @Getter
  @Setter
  public static class Grounding {

    /**
     * 单句支撑比下限：句内词被所选上下文覆盖的比例低于此值即判该句无支撑。
     *
     * <p>默认 0.6 是「改写句仍能过、编造句过不去」的经验水位；须按 golden set 与线上拒答率校准 （拒答率突增说明实体词被改写或分词口径变化）。
     */
    private double minSupportRatio = 0.6;

    /** 允许的无支撑句占比上限（默认 0 = 零容忍，任一句无支撑即整体拒答）。 */
    private double maxUnsupportedRatio = 0.0;

    /**
     * 语义接地裁判开关（{@code bone.ai.rag.grounding.judge-enabled}，<b>默认 false = opt-in</b>；R5）。
     *
     * <p>开启 = 确定性守卫过关后追加一次 NLI 语义裁判（经网关卡面，<b>另一次模型调用</b>）， paraphrase 编造（词面全对但结论上下文没给）才有运行时防线； 关闭 =
     * 仅确定性守卫（零额外成本，默认形态）。
     *
     * <p><b>为何默认关而非默认开（与 ACL 默认 deny 的取向不同，是成本与收益的分野）</b>：默认 deny ACL 无额外成本、放行即越权，
     * 故默认收紧；而语义裁判让<b>每次问答成本约翻倍</b>（生成 + 裁判两次模型调用），对成本敏感租户是硬负担。 且确定性守卫（无据拒答 / 引用强制 / 逐句词面支撑 /
     * 数字一致性）始终在岗，默认并非「不校验」。语义级运行时防线是<b>高合规场景按需开启</b>的增强层， 与离线评测期 faithfulness 判官（{@code
     * AnswerQualityJudge}，另一条独立度量的能力面）职责不同、不互相替代。
     *
     * <p>故障已设计为回落确定性守卫 + {@code GROUNDING_JUDGE_DEGRADED} 降级痕迹（不因裁判故障拒答），故开启后风险可控；
     * 关闭时响应不再出现该痕迹，监控可据此确认回退生效。
     */
    private boolean judgeEnabled = false;
  }

  @Getter
  @Setter
  public static class Rerank {
    /** 共享重排序服务 base-url（如 {@code http://tei:80}）；空则回退 NoOpReranker。 */
    private String baseUrl;

    private String model = "BAAI/bge-reranker-v2-m3";
    private Duration timeout = Duration.ofSeconds(10);
  }

  /**
   * 文档级 ACL 默认姿态（{@code bone.ai.rag.acl.*}，2026-10-24 企业复杂场景诊断 P0-2）。
   *
   * <p>默认 deny：未以 {@code @Bean} 提供 {@code RagAclPort} 实现时拒绝检索（空可见集），不放行全租户—— 企业单租户内 多部门 /
   * 多团队场景下，兜底放行即跨文档越权面。开发联调可显式关闭（回落 {@code NoDocAcl} 仅租户硬隔离，WARN 提示）。
   */
  @Getter
  @Setter
  public static class Acl {

    /**
     * 未接入 ACL 实现时是否默认拒绝检索（默认 true = fail-closed）。
     *
     * <p>false = 回落 {@code NoDocAcl}（仅租户硬隔离）：仅限开发联调，装配时打 WARN，禁止带入生产。
     */
    private boolean defaultDeny = true;
  }

  /** 写入侧配置（{@code bone.ai.rag.ingest.*}，2026-10-24 企业复杂场景诊断 P0-3）。 */
  @Getter
  @Setter
  public static class Ingest {

    /**
     * 写入侧 PII 字段级掩码开关（默认 true）。
     *
     * <p>ingest 时对高敏模式（身份证 / 手机号 / 银行卡 / 邮箱）做<b>等长局部掩码</b>（如身份证保留前 6 后 4）—— {@code chunk_text}
     * 明文落库与「数据不出域 / 最小暴露」不自洽（DB / 备份 / 运维通道泄露即明文外泄）。 等长掩码保证 {@code spanStart/spanEnd}
     * 锚点不漂移；保留前后片段维持部分检索语义。确认无需入库侧脱敏的合规评估后可显式关闭。
     */
    private boolean piiMaskEnabled = true;
  }
}
