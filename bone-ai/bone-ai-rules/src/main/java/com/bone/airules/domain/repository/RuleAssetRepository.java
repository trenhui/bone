package com.bone.airules.domain.repository;

import com.bone.airules.domain.model.RuleAsset;
import java.util.List;
import java.util.Optional;

/** 规则资产仓储（接口由使用方 {@code @EnableSqlRepositories(basePackages=...)} 生成实现）。 */
public interface RuleAssetRepository {

  Optional<RuleAsset> findById(String id);

  Optional<RuleAsset> findByCode(String tenantId, String code);

  /** 租户内全部生效规则（ACTIVE，含灰度中）。 */
  List<RuleAsset> findActive(String tenantId);

  void save(RuleAsset rule);
}
