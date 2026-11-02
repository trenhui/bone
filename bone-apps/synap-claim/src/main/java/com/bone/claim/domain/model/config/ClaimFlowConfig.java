package com.bone.claim.domain.model.config;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import java.util.Date;
import lombok.Getter;

/**
 * 租户级流程/钩子配置（§6.7 流程配置化 + 「新保司接入≤7天」承载）。
 *
 * <p><b>为什么需要</b>：新保司接入时不应为每个保司改代码，而是落一份流程/钩子配置（{@code config_json}）， 由生命周期引擎按 {@code flow_key}
 * 读取并驱动审批链、钩子与自动化规则。配置变更须带 {@code config_version} 发版号（禁止热改线上运行配置）。
 *
 * <p>本实体仅承载配置载体；具体的「按 flow 驱动生命周期」接线为后续工序（本批补齐表 + 实体 + 仓储，闭合方案 §9.5 表清单）。
 */
@Getter
@Table("t_claim_flow_config")
public class ClaimFlowConfig extends TenantAbstractEntity<Long> {

  @Column(name = "flow_key", nullable = false)
  private String flowKey;

  @Column(name = "config_version", nullable = false)
  private Integer configVersion;

  @Column(name = "config_json")
  private String configJson;

  @Column(name = "active_flag", nullable = false)
  private Integer activeFlag;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  ClaimFlowConfig() {}

  public static ClaimFlowConfig create(
      Long tenantId, String flowKey, int configVersion, String configJson, Instant now) {
    ClaimFlowConfig config = new ClaimFlowConfig();
    config.setTenantId(tenantId);
    config.flowKey = flowKey;
    config.configVersion = configVersion;
    config.configJson = configJson;
    config.activeFlag = 1;
    config.version = 0L;
    // 审计列由 SDK 映射但**不会自动填充**：DDL 两列 NOT NULL 无默认值，必须显式赋值
    Date nowDate = Date.from(now);
    config.setCreatedAt(nowDate);
    config.setUpdatedAt(nowDate);
    return config;
  }
}
