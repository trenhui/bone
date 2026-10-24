package com.bone.aiapp.web;

import com.bone.aiapp.config.AiTenantContext;
import com.bone.aiapp.config.BoneAiProperties;
import com.bone.airag.application.RagAnswerOutcome;
import com.bone.airag.application.RagAnswerService;
import com.bone.airag.application.RagApplicationService;
import com.bone.airag.application.RagApplicationService.RagIngestSegment;
import com.bone.airag.domain.model.RagQuery;
import com.bone.core.model.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生产拓扑：受控检索（文档级 ACL → 租户过滤 → 容量护栏 → 双路 + RRF → rerank → minScore → MMR → 引用强制 fail-closed）+ RAG
 * 回答闭环（R1 接线：检索 → 数据围栏提示词 → 网关卡面生成 → 接地守卫 → 拒答或返回）HTTP 能力。
 *
 * <p>provider 故障降级口径：嵌入 ⇒ BM25 单路；重排序 ⇒ RRF 粗序（均 WARN 事件 + 响应 {@code degradations} 痕迹，
 * 引用强制语义不变，禁切云端）。回答链拒答（{@code answered=false}）是可控业务结果：按 {@code errorCode} 走 i18n，不重试。
 */
@RestController
@RequestMapping("/api/ai/v1/rag")
public class RagController {

  private final RagApplicationService ragService;
  private final RagAnswerService ragAnswerService;
  private final BoneAiProperties props;
  private final AiTenantContext tenantContext;

  public RagController(
      RagApplicationService ragService,
      RagAnswerService ragAnswerService,
      BoneAiProperties props,
      AiTenantContext tenantContext) {
    this.ragService = ragService;
    this.ragAnswerService = ragAnswerService;
    this.props = props;
    this.tenantContext = tenantContext;
  }

  @PostMapping("/retrieve")
  public ApiResponse<RagRetrieveResponse> retrieve(@RequestBody RagRetrieveRequest req) {
    RagQuery query =
        queryOf(req.query(), req.docScope(), req.topK(), req.minScore(), req.maxScan());
    RagApplicationService.RagRetrieveOutcome outcome = ragService.retrieve(query);
    return ApiResponse.success(new RagRetrieveResponse(outcome.results(), outcome.degradations()));
  }

  /**
   * RAG 回答闭环（R1 接 G）：同一受控检索 + 「接地提示词 → 网关卡面生成 → 接地守卫」。
   *
   * <p>空检索不调模型（无据不生成）；生成被网关治理链拒绝（注入疑似 / 配额 / 红线 / 内容安全）或 provider 故障时收敛为可控拒答 （{@code
   * answered=false} + 保真 {@code errorCode}），不抛异常冲出接口。
   */
  @PostMapping("/answer")
  public ApiResponse<RagAnswerResponse> answer(@RequestBody RagAnswerRequest req) {
    RagQuery query =
        queryOf(req.query(), req.docScope(), req.topK(), req.minScore(), req.maxScan());
    RagAnswerOutcome outcome = ragAnswerService.answer(query);
    return ApiResponse.success(RagAnswerResponse.of(outcome));
  }

  @PostMapping("/ingest")
  public ApiResponse<Void> ingest(@RequestBody RagIngestRequest req) {
    List<RagIngestSegment> segments =
        req.segments().stream()
            .map(
                s ->
                    new RagIngestSegment(
                        s.sectionPath(), s.text(), s.clauseRef(), s.spanStart(), s.spanEnd()))
            .toList();
    ragService.ingest(
        tenantContext.requireNumericTenantId(), req.docId(), req.docVersion(), segments);
    return ApiResponse.success(null);
  }

  /** 检索 / 回答共用的查询装配：请求级护栏覆盖配置默认值（详设 §2.6）。 */
  private RagQuery queryOf(
      String query, String docScope, Integer topK, Double minScore, Integer maxScan) {
    return new RagQuery(
        query,
        docScope,
        topK != null ? topK : props.getRagTopK(),
        minScore != null ? minScore : props.getRagMinScore(),
        maxScan != null ? maxScan : props.getRagMaxScan());
  }
}
