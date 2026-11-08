package com.bone.claim.domain.model.mapping;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 承保主体主档（{@code t_claim_insurer}）。
 *
 * <p><b>为什么必须有这张表</b>：此前保单上的保险公司只是 {@code t_claim_policy.insurer_name} 自由文本，
 * 映射层无法用它做键——同一家保司被写成"永诚财险"/"永诚财产保险股份有限公司"时，映射会分裂成两套， 而运营完全看不出这是同一个主体。
 *
 * <p><b>分公司挂在同一张表</b>：{@code parentCode} 指向法人主体，法人自身为空。不建第二套实体，是因为
 * 分公司的码表与法人共用同一套报价/理赔口径，拆成两张表只会让"取哪一层"变成到处判断的分支。
 *
 * <p><b>不物理删除</b>：主体被保单、映射、赔案引用，删一行会产生孤儿；终止合作是 {@link InsurerStatus#TERMINATED}。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_insurer")
public class ClaimInsurer extends TenantAggregateRoot<Long> {

  @Column(name = "insurer_code")
  private String insurerCode;

  @Column(name = "insurer_name")
  private String insurerName;

  @Column(name = "insurer_short_name")
  private String insurerShortName = "";

  /** DIRECT 直保 / BROKER 经纪 / AGENT 代理。 */
  @Column(name = "insurer_type")
  private String insurerType = "DIRECT";

  /** 统一社会信用代码：外部桥锚点（保司码对不上时用它兜底）。 */
  @Column(name = "credit_code")
  private String creditCode = "";

  @Column(name = "parent_code")
  private String parentCode = "";

  @Column(name = "status")
  private String status = InsurerStatus.ACTIVE.code();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version")
  private Long version;

  public static ClaimInsurer of(
      Long id,
      Long tenantId,
      String insurerCode,
      String insurerName,
      String insurerShortName,
      String insurerType,
      String creditCode,
      String parentCode,
      Instant now) {
    if (insurerCode == null || insurerCode.isBlank()) {
      throw new DomainException("保司编码不能为空");
    }
    if (insurerName == null || insurerName.isBlank()) {
      throw new DomainException("保司名称不能为空: " + insurerCode);
    }
    ClaimInsurer e = new ClaimInsurer();
    e.setId(id);
    e.setTenantId(tenantId);
    e.insurerCode = insurerCode.trim();
    e.insurerName = insurerName.trim();
    e.insurerShortName = nullToEmpty(insurerShortName);
    e.insurerType = insurerType == null || insurerType.isBlank() ? "DIRECT" : insurerType.trim();
    e.creditCode = nullToEmpty(creditCode);
    e.parentCode = nullToEmpty(parentCode);
    e.status = InsurerStatus.ACTIVE.code();
    e.createdAt = now;
    e.updatedAt = now;
    return e;
  }

  public void applyEdit(
      String insurerName,
      String insurerShortName,
      String insurerType,
      String creditCode,
      Instant now) {
    if (insurerName == null || insurerName.isBlank()) {
      throw new DomainException("保司名称不能为空: " + insurerCode);
    }
    this.insurerName = insurerName.trim();
    this.insurerShortName = nullToEmpty(insurerShortName);
    this.insurerType =
        insurerType == null || insurerType.isBlank() ? this.insurerType : insurerType.trim();
    this.creditCode = nullToEmpty(creditCode);
    this.updatedAt = now;
  }

  /** 停用/终止合作：不删行，只改状态（历史单据仍要显示这家主体）。 */
  public void changeStatus(InsurerStatus target, Instant now) {
    if (target == null) {
      throw new DomainException("目标状态不能为空");
    }
    this.status = target.code();
    this.updatedAt = now;
  }

  public boolean isActive() {
    return InsurerStatus.ACTIVE.code().equals(status);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value.trim();
  }
}
