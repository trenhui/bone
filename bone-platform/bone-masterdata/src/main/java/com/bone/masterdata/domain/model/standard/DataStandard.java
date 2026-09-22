package com.bone.masterdata.domain.model.standard;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.masterdata.domain.model.standard.vo.StandardFieldCode;
import com.bone.masterdata.domain.model.standard.vo.StandardRuleType;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 数据标准聚合：编码规则 / 参考数据。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("meta_data_standard")
public class DataStandard extends TenantAggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "entity_code")
  private String entityCode;

  @Column(name = "field_code")
  private StandardFieldCode fieldCode;

  @Column(name = "rule_type")
  private StandardRuleType ruleType;

  private String pattern;

  @Column(name = "ref_code")
  private String refCode;

  private String description;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static DataStandard create(
      Long id,
      String entityCode,
      StandardFieldCode fieldCode,
      StandardRuleType ruleType,
      String pattern,
      String refCode,
      String description) {
    DataStandard standard = new DataStandard();
    standard.id = id;
    standard.entityCode = entityCode;
    standard.fieldCode = fieldCode;
    standard.ruleType = ruleType;
    standard.pattern = pattern;
    standard.refCode = refCode;
    standard.description = description;
    standard.createdAt = LocalDateTime.now();
    standard.updatedAt = LocalDateTime.now();
    return standard;
  }

  public void update(
      StandardRuleType ruleType, String pattern, String refCode, String description) {
    this.ruleType = ruleType;
    this.pattern = pattern;
    this.refCode = refCode;
    this.description = description;
    this.updatedAt = LocalDateTime.now();
  }
}
