package com.bone.airules.domain.model;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.annotation.Transient;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 规则资产（租户聚合根，bone-metadata-sdk 持久化）：治理面模型——DSL 文本 + 版本链 + 灰度 + 命中标记，不承载执行引擎。
 *
 * <p>表 {@code rule_asset}；{@code RuleAssetRepository extends Repository} 由 SDK
 * 动态代理（{@code @EnableSqlRepositories} 扫描），无需手写实现。执行交 {@code RuleEngine} SPI（QLExpress 唯一）；版本链
 * {@code versions} 暂 {@code @Transient} （批二以 {@code @Cascade} 落 {@code rule_asset_version} 子表）。
 */
@Getter
@Setter
@Table("rule_asset")
public class RuleAsset extends TenantAggregateRoot<String> {

  /**
   * 影子主键：应用生成 UUID。覆盖基类 {@code Entity.id} 以去掉 {@code @GeneratedValue} 的雪花生成（本聚合用 app UUID）； 不加
   * Lombok 访问器，沿用 {@code Entity} 的反射 getId/setId（子类字段优先，读写一致）。
   */
  @Id
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private String id;

  private String code; // 业务编码（R01–R25 等），租户内唯一
  private String name;
  private String description;
  private RuleStatus status = RuleStatus.DRAFT;

  @Column(name = "gray_percent")
  private int grayPercent; // 当前灰度 0-100（0=全量，100=全灰测）

  @Column(name = "hit_block")
  private boolean hitBlock; // RULE_HIT_BLOCK：命中时网关短路，不调 LLM

  @Column(name = "current_version_no")
  private int currentVersionNo = 0;

  private String expression; // 当前生效表达式（与 currentVersionNo 对应）

  @Deleted private Boolean deleted = false;

  @Transient private final List<RuleAssetVersion> versions = new ArrayList<>();

  public RuleAsset() {}

  /** 领域工厂：创建规则草稿（应用服务经工厂获得聚合，不得直接 new）。 */
  public static RuleAsset create(String tenantId, String code, String name, String description) {
    return new RuleAsset(UUID.randomUUID().toString(), tenantId, code, name, description);
  }

  private RuleAsset(String id, String tenantId, String code, String name, String description) {
    setId(id);
    setTenantId(tenantId);
    this.code = code;
    this.name = name;
    this.description = description;
  }

  /** 首次发布：创建 v1 版本快照并生效。草稿状态不允许执行。 */
  public RuleAssetVersion publishFirst(
      String expression, boolean hitBlock, int grayPercent, String operator) {
    if (status != RuleStatus.DRAFT) {
      throw new IllegalStateException("rule not in DRAFT: " + code);
    }
    return addVersion(expression, hitBlock, grayPercent, operator);
  }

  /** 发布新版本：版本 +1，替换当前生效表达式（历史版本留链供回放与 diff）。 */
  public RuleAssetVersion publishNext(
      String expression, boolean hitBlock, int grayPercent, String operator) {
    if (status == RuleStatus.DRAFT) {
      throw new IllegalStateException("rule not published yet: " + code);
    }
    return addVersion(expression, hitBlock, grayPercent, operator);
  }

  private RuleAssetVersion addVersion(
      String expression, boolean hitBlock, int grayPercent, String operator) {
    currentVersionNo++;
    RuleAssetVersion v =
        RuleAssetVersion.of(getId(), currentVersionNo, expression, hitBlock, grayPercent, operator);
    versions.add(v);
    this.expression = expression;
    this.hitBlock = hitBlock;
    this.grayPercent = grayPercent;
    this.status = RuleStatus.ACTIVE;
    return v;
  }

  public RuleAssetVersion latestVersion() {
    if (versions.isEmpty()) {
      return null;
    }
    return versions.get(versions.size() - 1);
  }

  public List<RuleAssetVersion> getVersions() {
    return Collections.unmodifiableList(versions);
  }

  /** 调整灰度比例（0-100；0=全量不灰度）。校验放领域，防非法值入链。 */
  public void changeGrayPercent(int grayPercent) {
    if (grayPercent < 0 || grayPercent > 100) {
      throw new IllegalArgumentException("grayPercent out of range: " + grayPercent);
    }
    this.grayPercent = grayPercent;
  }

  /** 命中标记开关：RULE_HIT_BLOCK——命中时网关短路不调 LLM。 */
  public void toggleHitBlock(boolean hitBlock) {
    this.hitBlock = hitBlock;
  }

  /** 灰度分桶判定：grayPercent=0 全量执行；否则按 code(+traceId) 稳定分桶。 */
  public boolean inGrayBucket(String traceId) {
    if (grayPercent <= 0 || grayPercent >= 100) {
      return true;
    }
    String salt = (traceId == null || traceId.isBlank()) ? code : code + ":" + traceId;
    int bucket = Math.abs(salt.hashCode()) % 100;
    return bucket < grayPercent;
  }

  /** 停用：表达式不再执行，版本链保留供回放。 */
  public void deprecate() {
    this.status = RuleStatus.DEPRECATED;
  }
}
