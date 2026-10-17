package com.bone.aiapp.web;

import com.bone.aiapp.config.BoneAiProperties;
import com.bone.airag.application.RagService;
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

  private final RagService ragService;
  private final BoneAiProperties props;

  public RagController(RagService ragService, BoneAiProperties props) {
    this.ragService = ragService;
    this.props = props;
  }

  @PostMapping("/retrieve")
  public ApiResponse<List<RagResult>> retrieve(@RequestBody RagRetrieveRequest req) {
    int topK = req.topK() != null ? req.topK() : props.getRagTopK();
    RagQuery query = new RagQuery(req.query(), req.docScope(), topK);
    return ApiResponse.success(ragService.retrieve(query));
  }
}
