package com.bone.claim.domain.model.sign;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 签收明细（子集合）：一个批次下的一条赔案及其影像完备标记。 */
@Getter
@NoArgsConstructor
@Table("t_claim_sign_batch_item")
public class SignBatchItem extends TenantAbstractEntity<Long> {

  @Column(name = "batch_id")
  private Long batchId;

  @Column(name = "claim_id")
  private Long claimId;

  @Column(name = "claim_no")
  private String claimNo;

  @Column(name = "image_ready")
  private Boolean imageReady;

  public static SignBatchItem of(long tenantId, long batchId, long claimId, String claimNo) {
    SignBatchItem item = new SignBatchItem();
    item.setTenantId(tenantId);
    item.batchId = batchId;
    item.claimId = claimId;
    item.claimNo = claimNo;
    item.imageReady = false;
    // SDK 的 @Cascade 插入不会自动填审计字段（同 ClaimItem 工厂），DB 侧 NOT NULL：
    // 不显式设置会在根 save 之后抛 "Column 'created_at' cannot be null"。
    java.util.Date now = new java.util.Date();
    item.setCreatedAt(now);
    item.setUpdatedAt(now);
    return item;
  }

  public boolean isImageReady() {
    return Boolean.TRUE.equals(imageReady);
  }

  public void markImageReady() {
    this.imageReady = true;
  }
}
