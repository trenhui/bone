package com.bone.airules.application;

import com.bone.airules.domain.model.RuleAsset;
import com.bone.airules.domain.repository.RuleAssetRepository;
import java.util.List;
import java.util.Optional;

/** 规则查询应用服务（只读，供管理面 / 编排节点消费）。 */
public class RuleQueryService {

  private final RuleAssetRepository repository;

  public RuleQueryService(RuleAssetRepository repository) {
    this.repository = repository;
  }

  public Optional<RuleAsset> findById(String id) {
    return repository.findById(id);
  }

  public Optional<RuleAsset> findByCode(String tenantId, String code) {
    return repository.findByCode(tenantId, code);
  }

  public List<RuleAsset> findActive(String tenantId) {
    return repository.findActive(tenantId);
  }
}
