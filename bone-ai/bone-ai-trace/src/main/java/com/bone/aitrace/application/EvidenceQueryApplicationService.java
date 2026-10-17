package com.bone.aitrace.application;

import com.bone.aitrace.domain.model.EvidenceSnapshot;
import com.bone.aitrace.domain.repository.EvidenceSnapshotRepository;
import java.util.List;

/** 证据回放查询（读侧；批一：按 traceId 批量回放 / 按 ID 单取）。 */
public class EvidenceQueryApplicationService {

  private final EvidenceSnapshotRepository repository;

  public EvidenceQueryApplicationService(EvidenceSnapshotRepository repository) {
    this.repository = repository;
  }

  public List<EvidenceSnapshot> replayByTraceId(String traceId) {
    return repository.findByTraceId(traceId);
  }

  public EvidenceSnapshot byId(Long id) {
    return repository.findById(id);
  }
}
