package com.bone.airules.domain.model;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 规则资产版本快照（不可变，bone-metadata-sdk 持久化，表 {@code rule_asset_version}）。
 *
 * <p>合规语义：版本链是「当时生效的是哪条表达式、谁发布的、灰度多少」的唯一真相——回放一次历史判定必须能取到当次版本， 而不是只能看到当前表达式（2026-10-17 复核修复：此前版本链为
 * {@code @Transient}，重启即丢，合规回放不可证）。
 *
 * <p>回滚语义：<b>不历史篡改</b>——回滚 = 以历史表达式发布一个新版本（版本号 +1），旧当前版打 {@code ROLLED_BACK} 标记。
 */
@Getter
@Setter
@Table("rule_asset_version")
public class RuleAssetVersion extends TenantAggregateRoot<String> {

  /** 版本状态：ACTIVE=当前生效链上 / ROLLED_BACK=已被回滚取代。 */
  public static final String STATUS_ACTIVE = "ACTIVE";

  public static final String STATUS_ROLLED_BACK = "ROLLED_BACK";

  /** 影子主键：应用生成 UUID（与父聚合 rule_asset 同款，去雪花生成）。 */
  @Id
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private String id;

  @Column(name = "rule_id")
  private String ruleId;

  @Column(name = "version_no")
  private int versionNo;

  private String expression; // 该版本 QLExpress DSL 文本

  @Column(name = "hit_block")
  private boolean hitBlock; // RULE_HIT_BLOCK：命中时网关短路，不调 LLM

  @Column(name = "gray_percent")
  private int grayPercent; // 灰度比例 0-100

  private String status = STATUS_ACTIVE;

  @Column(name = "created_by")
  private String createdBy; // 发布人

  @Deleted private Boolean deleted = false;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  public RuleAssetVersion() {}

  /** 领域工厂：落一个版本快照（发布动作经 RuleAsset.addVersion 调用，租户随父聚合）。 */
  public static RuleAssetVersion of(
      String tenantId,
      String ruleId,
      int versionNo,
      String expression,
      boolean hitBlock,
      int grayPercent,
      String createdBy) {
    RuleAssetVersion v = new RuleAssetVersion();
    v.setId(UUID.randomUUID().toString());
    v.setTenantId(tenantId);
    v.ruleId = ruleId;
    v.versionNo = versionNo;
    v.expression = expression;
    v.hitBlock = hitBlock;
    v.grayPercent = grayPercent;
    v.status = STATUS_ACTIVE;
    v.createdBy = createdBy;
    LocalDateTime now = LocalDateTime.now();
    v.createdAt = now;
    v.updatedAt = now;
    return v;
  }

  /** 标记为已被回滚取代（不删除——历史版本留链供回放）。 */
  public void markRolledBack() {
    this.status = STATUS_ROLLED_BACK;
  }
}
