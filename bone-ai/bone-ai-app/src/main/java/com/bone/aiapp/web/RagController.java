package com.bone.aiapp.web;

import com.bone.aiapp.config.AiTenantContext;
import com.bone.aiapp.config.BoneAiProperties;
import com.bone.airag.application.RagApplicationService;
import com.bone.airag.application.RagApplicationService.RagIngestSegment;
import com.bone.airag.domain.model.RagQuery;
import com.bone.airag.domain.model.RagResult;
import com.bone.core.model.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 生产拓扑：受控检索（双路 + RRF 融合 + 引用强制 fail-closed）HTTP 能力。 */
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
  public ApiResponse<List<RagResult>> retrieve(@RequestBody RagRetrieveRequest req) {
    int topK = req.topK() != null ? req.topK() : props.getRagTopK();
    RagQuery query = new RagQuery(req.query(), req.docScope(), topK);
    return ApiResponse.success(ragService.retrieve(query));
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
    ragService.ingest(tenantContext.requireNumericTenantId(), req.docId(), segments);
    return ApiResponse.success(null);
  }
}
