package com.bone.aiapp.web;

import com.bone.aiapp.config.AiTenantContext;
import com.bone.aiapp.config.BoneAiProperties;
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
 * 生产拓扑：受控检索（文档级 ACL → 租户过滤 → 容量护栏 → 双路 + RRF → rerank → minScore → MMR → 引用强制 fail-closed）HTTP 能力。
 *
 * <p>provider 故障降级口径：嵌入 ⇒ BM25 单路；重排序 ⇒ RRF 粗序（均 WARN 事件 + 响应 {@code degradations} 痕迹，
 * 引用强制语义不变，禁切云端）。
 */
@RestController
@RequestMapping("/api/ai/v1/rag")
public class RagController {

  private final RagApplicationService ragService;
  private final BoneAiProperties props;
  private final AiTenantContext tenantContext;

  public RagController(
      RagApplicationService ragService, BoneAiProperties props, AiTenantContext tenantContext) {
    this.ragService = ragService;
    this.props = props;
    this.tenantContext = tenantContext;
  }

  @PostMapping("/retrieve")
  public ApiResponse<RagRetrieveResponse> retrieve(@RequestBody RagRetrieveRequest req) {
    int topK = req.topK() != null ? req.topK() : props.getRagTopK();
    double minScore = req.minScore() != null ? req.minScore() : props.getRagMinScore();
    int maxScan = req.maxScan() != null ? req.maxScan() : props.getRagMaxScan();
    RagQuery query = new RagQuery(req.query(), req.docScope(), topK, minScore, maxScan);
    RagApplicationService.RagRetrieveOutcome outcome = ragService.retrieve(query);
    return ApiResponse.success(new RagRetrieveResponse(outcome.results(), outcome.degradations()));
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
}
