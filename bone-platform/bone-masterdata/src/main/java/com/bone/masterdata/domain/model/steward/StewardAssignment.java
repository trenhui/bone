package com.bone.masterdata.domain.model.steward;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 治理角色指派（G3，UC-T2）：数据 Owner / Steward / Approver（SoD 由应用层校验）。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("mdm_steward")
public class StewardAssignment extends TenantAggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "mdm_entity_id")
  private Long masterDataEntityId;

  /** IAM 账号ID。 */
  @Column(name = "account_id")
  private Long accountId;

  /** 角色：OWNER / STEWARD / APPROVER。 */
  @Column(name = "role_type")
  private String roleType;

  private LocalDateTime createdAt;

  /**
   * 最后变更时间（撤销指派时刷新，scripts/migration/0018 补齐 HC-008 必备列）。
   *
   * <p>bone-metadata-sdk **不自动填充** {@code updated_at}（通用 insert/update 不写该列）， 必须由领域行为显式赋值，范式同
   * {@code MasterDataEntity}。缺这一列时 {@code createdAt} 只能回答"何时授的"，撤销后无从追溯"何时撤的"，治理审计链断裂。
   */
  private LocalDateTime updatedAt;

  /**
   * 软删标记。
   *
   * <p><b>此字段不是可选的</b>：bone-metadata-sdk 的 {@code TableMetadata#isSoftDeletable()}判据是 **实体内是否存在带
   * {@link com.bone.core.annotation.Deleted} 注解的字段**，与 DDL 是否有 {@code deleted} 列无关。{@code
   * TenantAggregateRoot} 只提供 {@code tenantId}（其 javadoc 明示"审计由子类自行声明"）， 不像 {@code AbstractEntity}
   * 那样自带 {@code @Deleted deleted}。本类若不声明， {@code Repository#deleteById} 会判定"不可软删"从而发出 {@code DELETE
   * FROM} —— 治理指派被撤销时 整行物理消失，撤销时刻与历史指派均不可追溯，而调用方返回 200 毫不知情。
   *
   * <p>实测确认（2026-10-03）：本字段缺失时unassign 后 mdm_steward 行数由 1 变 0。
   */
  @Deleted private Boolean deleted = false;

  public static StewardAssignment assign(
      Long id, Long masterDataEntityId, Long accountId, String roleType) {
    StewardAssignment assignment = new StewardAssignment();
    assignment.id = id;
    assignment.masterDataEntityId = masterDataEntityId;
    assignment.accountId = accountId;
    assignment.roleType = roleType;
    assignment.createdAt = LocalDateTime.now();
    assignment.updatedAt = assignment.createdAt;
    return assignment;
  }

  /**
   * 撤销指派（领域行为）：调用方随后走 {@code Repository#deleteById} 软删，本方法先把 {@code updatedAt}
   * 落到"撤销时刻"，使软删后的行仍能回答"该角色何时被撤"。
   */
  public void revoke() {
    this.updatedAt = LocalDateTime.now();
  }
}
