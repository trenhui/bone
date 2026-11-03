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
 * 保司码表项（{@code t_claim_insurer_code_item}）：只承载"这家保司怎么叫"，不复制标准侧属性。
 *
 * <p><b>为什么不复制等级/规格等标准属性</b>：复制会产生两份真源——保司说"三甲"，标准库说"三级甲等"， 两边同时改就永远对不齐。附加属性只放在 {@code extraJson}
 * 里作为<b>匹配证据</b>，不参与业务判断。
 *
 * <p><b>同码复活走 UPDATE status，不插新行</b>：保司侧码值是它内部稳定身份；重建行会让历史映射指向 一条"新身份"，等于悄悄改写了历史。唯一键因此不含 {@code
 * deleted}。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_insurer_code_item")
public class InsurerCodeItem extends TenantAggregateRoot<Long> {

  @Column(name = "insurer_code")
  private String insurerCode;

  @Column(name = "domain")
  private String domain;

  @Column(name = "item_code")
  private String itemCode;

  @Column(name = "item_name")
  private String itemName;

  /** 归一化名称（去空格/全半角/括号），只用于候选匹配，不用于判定。 */
  @Column(name = "name_normalized")
  private String nameNormalized;

  @Column(name = "extra_json")
  private String extraJson;

  @Column(name = "status")
  private String status = CodeItemStatus.ACTIVE.code();

  @Column(name = "source_batch_no")
  private String sourceBatchNo = "";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version")
  private Long version;

  public static InsurerCodeItem of(
      Long id,
      Long tenantId,
      String insurerCode,
      MasterDataDomain domain,
      String itemCode,
      String itemName,
      String extraJson,
      String sourceBatchNo,
      Instant now) {
    if (insurerCode == null || insurerCode.isBlank()) {
      throw new DomainException("保司编码不能为空");
    }
    if (domain == null) {
      throw new DomainException("主数据域不能为空");
    }
    if (itemCode == null || itemCode.isBlank()) {
      throw new DomainException("保司侧码值不能为空");
    }
    InsurerCodeItem e = new InsurerCodeItem();
    e.setId(id);
    e.setTenantId(tenantId);
    e.insurerCode = insurerCode.trim();
    e.domain = domain.code();
    e.itemCode = itemCode.trim();
    e.itemName = itemName == null ? "" : itemName.trim();
    e.nameNormalized = NameNormalizer.normalize(itemName);
    e.extraJson = extraJson;
    e.status = CodeItemStatus.ACTIVE.code();
    e.sourceBatchNo = sourceBatchNo == null ? "" : sourceBatchNo.trim();
    e.createdAt = now;
    e.updatedAt = now;
    return e;
  }

  public void applyEdit(String itemName, String extraJson, Instant now) {
    this.itemName = itemName == null ? "" : itemName.trim();
    this.nameNormalized = NameNormalizer.normalize(itemName);
    this.extraJson = extraJson;
    this.updatedAt = now;
  }

  public void retire(Instant now) {
    this.status = CodeItemStatus.RETIRED.code();
    this.updatedAt = now;
  }

  public void reactivate(Instant now) {
    this.status = CodeItemStatus.ACTIVE.code();
    this.updatedAt = now;
  }

  public boolean isActive() {
    return CodeItemStatus.ACTIVE.code().equals(status);
  }
}
