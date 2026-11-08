package com.bone.claim.adapter.web.assembler;

import com.bone.claim.adapter.web.dto.response.EvidenceResp;
import com.bone.claim.domain.model.evidence.Evidence;
import java.util.List;

/** 证据材料响应装配的唯一真源。 */
public final class EvidenceAssembler {

  private EvidenceAssembler() {}

  public static List<EvidenceResp> toResp(List<Evidence> evidences) {
    if (evidences == null || evidences.isEmpty()) {
      return List.of();
    }
    return evidences.stream().map(EvidenceResp::of).toList();
  }
}
