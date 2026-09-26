package com.bone.masterdata.domain.model.steward;

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

  public static StewardAssignment assign(
      Long id, Long masterDataEntityId, Long accountId, String roleType) {
    StewardAssignment assignment = new StewardAssignment();
    assignment.id = id;
    assignment.masterDataEntityId = masterDataEntityId;
    assignment.accountId = accountId;
    assignment.roleType = roleType;
    assignment.createdAt = LocalDateTime.now();
    return assignment;
  }
}
