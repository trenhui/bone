package com.bone.system.application.query;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.system.domain.config.SystemConfig;
import com.bone.system.domain.model.config.vo.ConfigKey;
import com.bone.system.domain.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 系统配置唯一性查询（应用读侧）。
 *
 * <p>读侧 DSL（{@code Criteria}）只允许出现在应用读侧：写用例（command handler）与 domain 层禁止直接依赖读侧 能力（CORE-05 /
 * E-4.2），故「配置键唯一」判定由此读侧服务承载，由写用例调用而非自行查询。
 */
@Component
@RequiredArgsConstructor
public class ConfigUniquenessQuery {

  private final SystemConfigRepository systemConfigRepository;

  /** 配置键是否已存在（软删记录不计入）。 */
  public boolean existsByConfigKey(ConfigKey configKey) {
    SystemConfig existing =
        systemConfigRepository.findOneByCriteria(
            Criteria.<SystemConfig>create()
                .entityClass(SystemConfig.class)
                .eq("configKey", configKey));
    return existing != null;
  }
}
