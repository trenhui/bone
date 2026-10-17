package com.bone.airules.application;

import com.bone.airules.domain.model.RuleAsset;
import com.bone.airules.domain.model.RuleAssetVersion;
import com.bone.airules.domain.repository.RuleAssetRepository;
import com.bone.airules.domain.repository.RuleAssetVersionRepository;
import java.util.List;
import java.util.Optional;

/** 规则查询应用服务（只读，供管理面 / 编排节点 / 回放消费）。 */
public class RuleQueryApplicationService {

  private final RuleAssetRepository repository;
  private final RuleAssetVersionRepository versionRepository;

  public RuleQueryApplicationService(
      RuleAssetRepository repository, RuleAssetVersionRepository versionRepository) {
    this.repository = repository;
    this.versionRepository = versionRepository;
  }

  public Optional<RuleAsset> findById(String id) {
    return Optional.ofNullable(repository.findById(id));
  }

  public Optional<RuleAsset> findByCode(String tenantId, String code) {
    return repository.findByCode(tenantId, code);
  }

  public List<RuleAsset> findActive(String tenantId) {
    return repository.findActive(tenantId);
  }

  /** 版本链（版本号降序——回放「当时生效表达式」/ 版本 diff 的管理面读）。 */
  public List<RuleAssetVersion> history(String ruleId) {
    return versionRepository.findByRuleId(ruleId);
  }
}
