package com.bone.masterdata.domain.model.entity;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.model.entity.event.MasterDataEntityCreatedEvent;
import com.bone.masterdata.domain.model.entity.event.MasterDataEntityPublishedEvent;
import com.bone.masterdata.domain.model.entity.valueobject.MasterDataEntityName;
import com.bone.masterdata.domain.model.entity.valueobject.MasterDataEntityStatus;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 主数据实体聚合根。
// G1 收敛：持久化目标由 md_entity 切换为 mdm_entity（DDL 真源 bone-init.sql §8）。
// 承载 §3.2 三层归属（建模来源 metaEntityId / 责任归口 owningAppId / 消费订阅另表）
// 与 §4.3 治理等级 governanceTier。
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("mdm_entity")
public class MasterDataEntity extends TenantAggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "meta_entity_id")
  private Long metaEntityId;

  /** 主数据实体编码（租户内唯一）；存量迁移数据可为空。 */
  private String entityCode;

  /** 显示名称；列名 entity_name（保留 getName() 语义以兼容既有调用方）。 */
  private MasterDataEntityName entityName;

  private String description;
  private String category;

  /** 业务域编码，见 §3.1 首批域模板清单。 */
  private String domainCode;

  /** 来源域模板ID；为空表示租户自建。 */
  private Long templateId;

  /** 实例化时的模板版本号，用于 UC-P2 升级比对。 */
  private String templateVersion;

  /** 责任归口应用ID；为空表示平台共享域（§3.2）。 */
  private Long owningAppId;

  /** 治理等级：L1 轻量 / L2 标准 / L3 严格（§4.3）。 */
  private String governanceTier;

  /** 是否启用版本控制，由治理等级推导。 */
  private Boolean isVersioning;

  /** 是否启用审批流，由治理等级推导。 */
  private Boolean workflowEnabled;

  private MasterDataEntityStatus status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** 兼容既有调用方：getName() 等价于 getEntityName()。 */
  public MasterDataEntityName getName() {
    return this.entityName;
  }

  public static MasterDataEntity create(
      Long id, Long metaEntityId, MasterDataEntityName name, String description, String category) {
    MasterDataEntity entity = new MasterDataEntity();
    entity.id = id;
    entity.metaEntityId = metaEntityId;
    entity.entityName = name;
    entity.description = description;
    entity.category = category;
    entity.governanceTier = GovernanceTier.L1.name();
    entity.isVersioning = false;
    entity.workflowEnabled = false;
    entity.status = MasterDataEntityStatus.DRAFT;
    entity.createdAt = LocalDateTime.now();
    entity.updatedAt = LocalDateTime.now();
    entity.addDomainEvent(new MasterDataEntityCreatedEvent(entity));
    return entity;
  }

  // 从域模板实例化（UC-T1 主流程 A）：携带域编码、模板追溯与模板建议的治理等级。
  public static MasterDataEntity createFromTemplate(
      Long id,
      String entityCode,
      MasterDataEntityName name,
      String description,
      String domainCode,
      Long templateId,
      String templateVersion,
      String governanceTier) {
    MasterDataEntity entity = new MasterDataEntity();
    entity.id = id;
    entity.entityCode = entityCode;
    entity.entityName = name;
    entity.description = description;
    entity.domainCode = domainCode;
    entity.templateId = templateId;
    entity.templateVersion = templateVersion;
    entity.governanceTier = governanceTier == null ? GovernanceTier.L1.name() : governanceTier;
    entity.applyGovernanceTier(entity.governanceTier);
    entity.status = MasterDataEntityStatus.DRAFT;
    entity.createdAt = LocalDateTime.now();
    entity.updatedAt = LocalDateTime.now();
    entity.addDomainEvent(new MasterDataEntityCreatedEvent(entity));
    return entity;
  }

  /** 绑定责任归口应用（§3.2 owning_app_id，可空表示平台共享域）。 */
  public void bindOwningApp(Long owningAppId) {
    this.owningAppId = owningAppId;
    this.updatedAt = LocalDateTime.now();
  }

  // 调整治理等级（§4.3）：等级下调需调用方二次确认，领域层仅负责推导开关。
  public void changeGovernanceTier(String tier) {
    GovernanceTier resolved = GovernanceTier.of(tier);
    this.governanceTier = resolved.name();
    applyGovernanceTier(this.governanceTier);
    this.updatedAt = LocalDateTime.now();
  }

  private void applyGovernanceTier(String tier) {
    GovernanceTier resolved = GovernanceTier.of(tier);
    this.isVersioning = resolved.isVersioning();
    this.workflowEnabled = resolved.isWorkflow();
  }

  public void publish() {
    if (this.status == MasterDataEntityStatus.PUBLISHED) {
      throw new DomainException("主数据实体已发布");
    }
    this.status = MasterDataEntityStatus.PUBLISHED;
    this.updatedAt = LocalDateTime.now();
    this.addDomainEvent(new MasterDataEntityPublishedEvent(this));
  }

  public void update(MasterDataEntityName name, String description, String category) {
    if (this.status == MasterDataEntityStatus.PUBLISHED) {
      throw new DomainException("已发布的主数据实体不能修改");
    }
    this.entityName = name;
    this.description = description;
    this.category = category;
    this.updatedAt = LocalDateTime.now();
  }

  public void disable() {
    if (this.status == MasterDataEntityStatus.DISABLED) {
      throw new DomainException("主数据实体已停用");
    }
    this.status = MasterDataEntityStatus.DISABLED;
    this.updatedAt = LocalDateTime.now();
  }

  public void enable() {
    if (this.status != MasterDataEntityStatus.DISABLED) {
      throw new DomainException("主数据实体未处于停用状态");
    }
    this.status = MasterDataEntityStatus.DRAFT;
    this.updatedAt = LocalDateTime.now();
  }

  /** 治理等级枚举（§4.3）：L1 轻量 / L2 标准 / L3 严格。 */
  public enum GovernanceTier {
    L1(false, false),
    L2(true, true),
    L3(true, true);

    private final boolean versioning;
    private final boolean workflow;

    GovernanceTier(boolean versioning, boolean workflow) {
      this.versioning = versioning;
      this.workflow = workflow;
    }

    public static GovernanceTier of(String tier) {
      if (tier == null || tier.isBlank()) {
        return L1;
      }
      try {
        return GovernanceTier.valueOf(tier.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new DomainException("非法的治理等级：" + tier);
      }
    }

    public boolean isVersioning() {
      return versioning;
    }

    public boolean isWorkflow() {
      return workflow;
    }
  }
}
