package com.bone.airules.domain.model;

import com.bone.core.domain.TenantAggregateRoot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 规则资产（租户聚合根）：治理面模型——DSL 文本 + 版本链 + 灰度 + 命中标记，不承载执行引擎。
 *
 * <p>执行交给 {@code RuleEngine} SPI（QLExpress 唯一实现）；本聚合只管理资产与发布语义： 首版发布 / 新版本 / 灰度 / 命中标记开关 / 启停。
 */
@Getter
@Setter
public class RuleAsset extends TenantAggregateRoot<String> {

  private String code; // 业务编码（R01–R25 等），租户内唯一
  private String name;
  private String description;
  private RuleStatus status = RuleStatus.DRAFT;
  private int grayPercent; // 当前灰度 0-100（0=全量，100=全灰测）
  private boolean hitBlock; // RULE_HIT_BLOCK：命中时网关短路，不调 LLM
  private int currentVersionNo = 0;
  private String expression; // 当前生效表达式（与 currentVersionNo 对应）
  private final List<RuleAssetVersion> versions = new ArrayList<>();

  public RuleAsset() {}

  /** 领域工厂：创建规则草稿（应用服务经工厂获得聚合，不得直接 new）。 */
  public static RuleAsset create(String tenantId, String code, String name, String description) {
    return new RuleAsset(java.util.UUID.randomUUID().toString(), tenantId, code, name, description);
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
