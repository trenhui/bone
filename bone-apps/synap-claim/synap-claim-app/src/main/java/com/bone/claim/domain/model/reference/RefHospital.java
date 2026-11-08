package com.bone.claim.domain.model.reference;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import lombok.Getter;

/**
 * 基础目录——医院（{@code t_ref_hospital}，租户级主数据，0094）。
 *
 * <p>责任的适用限定名单（{@code t_adj_liability.limit_hospital_codes} 分隔串）的编码来源即本库：
 * 配置面从库里选，理算引擎只认编码串——目录只服务录入， 不参与理算，因此改目录不影响已配置责任。
 */
@Getter
@Table("t_ref_hospital")
public class RefHospital extends TenantAggregateRoot<Long> {

  @Column(name = "hospital_code")
  private String hospitalCode;

  @Column(name = "hospital_name")
  private String hospitalName;

  /** 医院等级（三甲/三乙/二甲等）。 */
  @Column(name = "hospital_level")
  private String hospitalLevel;

  /** 医保定点（1是/0否）。 */
  @Column(name = "social_flag")
  private Integer socialFlag;

  @Column(name = "province")
  private String province;

  @Column(name = "city")
  private String city;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version")
  private Long version;

  public static RefHospital of(
      Long id,
      Long tenantId,
      String hospitalCode,
      String hospitalName,
      String hospitalLevel,
      Integer socialFlag,
      String province,
      String city) {
    RefHospital e = new RefHospital();
    e.setId(id);
    e.setTenantId(tenantId);
    e.hospitalCode = hospitalCode == null ? "" : hospitalCode.trim();
    e.hospitalName = hospitalName == null ? "" : hospitalName.trim();
    e.hospitalLevel = hospitalLevel == null ? "" : hospitalLevel.trim();
    e.socialFlag = socialFlag == null ? 1 : socialFlag;
    e.province = province == null ? "" : province.trim();
    e.city = city == null ? "" : city.trim();
    Instant now = Instant.now();
    e.createdAt = now;
    e.updatedAt = now;
    return e;
  }

  /** 编辑目录项（编码是主数据键不可改；改名/等级等不影响已配置的责任名单）。 */
  public void applyEdit(
      String hospitalName, String hospitalLevel, Integer socialFlag, String province, String city) {
    this.hospitalName = hospitalName == null ? this.hospitalName : hospitalName.trim();
    this.hospitalLevel = hospitalLevel == null ? "" : hospitalLevel.trim();
    this.socialFlag = socialFlag == null ? this.socialFlag : socialFlag;
    this.province = province == null ? "" : province.trim();
    this.city = city == null ? "" : city.trim();
    this.updatedAt = Instant.now();
  }
}
