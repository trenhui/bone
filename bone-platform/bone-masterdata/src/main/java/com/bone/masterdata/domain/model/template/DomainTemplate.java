package com.bone.masterdata.domain.model.template;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.model.template.valueobject.DomainTemplateStatus;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 主数据域模板聚合根（G9，UC-P1/P2）：平台侧维护「域编码 → 默认字段集 / 质量规则包 / 分类骨架」， 租户只读实例化（{@code mdm_domain_template}，DDL
 * 真源 bone-init.sql §8）。
 *
 * <p><b>非租户作用域（2026-09-26 实测裁决）</b>：本表是「平台只写、多方只读」的全局目录——写入被 {@code
 * masterdata:templates:write}（平台域权限，租户角色未绑定）门禁控制，行内容恒为平台资产。若继承 {@code TenantAggregateRoot}，SDK
 * Criteria 通道会注入严格 {@code tenant_id = :current} 过滤，租户读 {@code tenant_id=0} 平台模板行 404、实例化不可用（实测复现）；
 * 而 FluentQuery 通道又不过滤，两通道行为分裂。故本聚合不映射 tenant_id（DDL 列保留，DEFAULT 0），读侧天然全平台可见、越权面为零。
 *
 * <p>版本演进走 {@link TemplateVersion}，租户自主选择是否跟随升级。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("mdm_domain_template")
public class DomainTemplate extends AggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  /** 域编码：CUSTOMER/SUPPLIER/MATERIAL/ORG_UNIT/FIN_ACCOUNT/EMPLOYEE/PROJECT，租户内唯一。 */
  @Column(name = "domain_code")
  private String domainCode;

  @Column(name = "domain_name")
  private String domainName;

  private String description;

  /** 当前版本号（semver）；发布新版本时推进。 */
  @Column(name = "current_version")
  private String currentVersion;

  /** 默认治理等级：L1/L2/L3，实例化时可被租户下调（§4.3）。 */
  @Column(name = "default_governance_tier")
  private String defaultGovernanceTier;

  /** 默认字段集（JSON 数组：[{code,name,type,length,required,defaultValue}]）。 */
  @Column(name = "field_schema")
  private String fieldSchema;

  /** 默认质量规则包（JSON 数组）。 */
  @Column(name = "rule_schema")
  private String ruleSchema;

  /** 默认分类骨架（JSON 树）。 */
  @Column(name = "category_schema")
  private String categorySchema;

  private DomainTemplateStatus status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static DomainTemplate create(
      Long id,
      String domainCode,
      String domainName,
      String description,
      String defaultGovernanceTier,
      String fieldSchema,
      String ruleSchema,
      String categorySchema) {
    DomainTemplate template = new DomainTemplate();
    template.id = id;
    template.domainCode = domainCode;
    template.domainName = domainName;
    template.description = description;
    template.currentVersion = "1.0.0";
    template.defaultGovernanceTier =
        defaultGovernanceTier == null || defaultGovernanceTier.isBlank()
            ? "L1"
            : defaultGovernanceTier;
    template.fieldSchema = fieldSchema;
    template.ruleSchema = ruleSchema;
    template.categorySchema = categorySchema;
    template.status = DomainTemplateStatus.DRAFT;
    template.createdAt = LocalDateTime.now();
    template.updatedAt = LocalDateTime.now();
    return template;
  }

  public void update(
      String domainName,
      String description,
      String fieldSchema,
      String ruleSchema,
      String categorySchema,
      String defaultGovernanceTier) {
    if (this.status == DomainTemplateStatus.PUBLISHED) {
      throw new DomainException("已发布的域模板不能直接修改，请新建版本（UC-P2）");
    }
    this.domainName = domainName;
    this.description = description;
    this.fieldSchema = fieldSchema;
    this.ruleSchema = ruleSchema;
    this.categorySchema = categorySchema;
    if (defaultGovernanceTier != null && !defaultGovernanceTier.isBlank()) {
      this.defaultGovernanceTier = defaultGovernanceTier;
    }
    this.updatedAt = LocalDateTime.now();
  }

  /** 发布新版本（UC-P2）：快照当前 schema 到版本表并推进 current_version。 */
  public void publishVersion(String versionNumber) {
    if (versionNumber == null || versionNumber.isBlank()) {
      throw new DomainException("版本号不能为空");
    }
    if (this.status == DomainTemplateStatus.ARCHIVED) {
      throw new DomainException("已归档的域模板不能再发布版本");
    }
    this.currentVersion = versionNumber;
    this.status = DomainTemplateStatus.PUBLISHED;
    this.updatedAt = LocalDateTime.now();
  }

  public void archive() {
    if (this.status == DomainTemplateStatus.ARCHIVED) {
      throw new DomainException("域模板已归档");
    }
    this.status = DomainTemplateStatus.ARCHIVED;
    this.updatedAt = LocalDateTime.now();
  }
}
