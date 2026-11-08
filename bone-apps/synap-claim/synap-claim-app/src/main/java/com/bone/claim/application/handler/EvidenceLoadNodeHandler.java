package com.bone.claim.application.handler;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.DecisionNodeHandler;
import com.bone.claim.domain.model.decision.DecisionGraph;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 决策节点 {@code EvidenceLoad}（OCR 类）：证据齐备度/解析度/OCR 置信度。
 *
 * <p>仅把 {@link ClaimContextPort} 的视图投影为执行器契约键（hasEvidence/evidenceParsed/ocrConfidence），
 * 不持有任何领域规则——解析判定逻辑在领域与端口适配层。
 */
@Component
public class EvidenceLoadNodeHandler implements DecisionNodeHandler {

  private final ClaimContextPort claimContextPort;

  public EvidenceLoadNodeHandler(ClaimContextPort claimContextPort) {
    this.claimContextPort = claimContextPort;
  }

  @Override
  public String nodeName() {
    return DecisionGraph.EVIDENCE_LOAD;
  }

  @Override
  public DecisionNodeOutcome execute(DecisionNodeContext context) {
    ClaimContextPort.ClaimContextView v = claimContextPort.getContext(context.claimId());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("hasEvidence", v.hasEvidence());
    out.put("evidenceParsed", v.evidenceAllParsed());
    out.put("ocrConfidence", v.minOcrConfidence());
    String summary =
        "evidence="
            + v.hasEvidence()
            + ",parsed="
            + v.evidenceAllParsed()
            + ",ocr="
            + v.minOcrConfidence();
    return DecisionNodeOutcome.success(out, null, summary, "OK");
  }
}
