package com.bone.metadata.catalog.domain.model.template;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 平台模型模板聚合根（G3，ADR-0031）：平台沉淀「系统长什么样」的可复用模型蓝图。
 *
 * <p>归属：平台层（{@code tenant_id = 0}），全部租户共享只读；模板维护属平台建模架构师职责（权限码 {@code
 * metadata:template:write}）。租户经「实例化」把模板复制为自己的 {@code meta_entity}（{@code scope=TENANT}、 记录 {@code
 * template_id} 追溯），平台模板升级不强制推送（UC-MP2）。
 */
@Getter
@NoArgsConstructor
@Table("meta_model_template")
public class MetaModelTemplate extends AbstractEntity<Long> {

  /** 归属层取值：meta_entity.scope 同口径。 */
  public static final String SCOPE_PLATFORM = "PLATFORM";

  public static final String SCOPE_TENANT = "TENANT";

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "description")
  private String description;

  /** 业务域分类（客户 / 订单 / 支付 / 物料 / 组织…），供模板目录检索。 */
  @Column(name = "category")
  private String category;

  @Column(name = "current_version", nullable = false)
  private String currentVersion;

  /** 0=草稿 1=已发布（已发布模板方可被租户实例化）。 */
  @Column(name = "status", nullable = false)
  private Integer status;

  /**
   * 交付模式（0041）：0=GENERATIVE 生成式交付（不建运行时物理表）；1=RUNTIME 运行时元数据面（发布即建表对齐）。
   *
   * <p>与 {@code meta_entity.delivery_mode} 同口径。模板是蓝图——交付模式在模板侧声明、实例化侧继承， 而不是每次实例化让调用方重猜（漏传
   * GENERATIVE 会「发布成功但无表」，E2E 实证坑）。
   */
  @Column(name = "delivery_mode", nullable = false)
  private Integer deliveryMode;

  public static MetaModelTemplate create(
      Long id,
      String code,
      String name,
      String description,
      String category,
      String currentVersion) {
    if (code == null || code.isBlank()) {
      throw new com.bone.core.exception.DomainException("模板编码不能为空");
    }
    if (name == null || name.isBlank()) {
      throw new com.bone.core.exception.DomainException("模板名称不能为空");
    }
    MetaModelTemplate t = new MetaModelTemplate();
    t.setId(id);
    // 平台层固定 tenant_id=0（2a §3.1 三层归属的顶层）
    t.tenantId = 0L;
    t.code = code;
    t.name = name;
    t.description = description;
    t.category = category;
    t.currentVersion = currentVersion != null ? currentVersion : "v1.0.0";
    t.status = 0;
    // 保守默认：不建表的清理成本远低于建错表；需要运行时语义的模板由种子/维护显式置 1
    t.deliveryMode = 0;
    Date now = new Date();
    t.setCreatedAt(now);
    t.setUpdatedAt(now);
    t.setDeleted(false);
    return t;
  }

  /** 发布模板：已发布后方可被租户实例化。 */
  public void publish() {
    this.status = 1;
    this.setUpdatedAt(new Date());
  }

  /** 读侧重建（含 status / deliveryMode）：仅 infrastructure/query 装配领域对象使用，不做校验性变更。 */
  public static MetaModelTemplate reconstitute(
      Long id,
      String code,
      String name,
      String description,
      String category,
      String currentVersion,
      int status,
      int deliveryMode) {
    MetaModelTemplate t = create(id, code, name, description, category, currentVersion);
    t.status = status;
    t.deliveryMode = deliveryMode;
    return t;
  }

  /** 交付模式枚举视图（与 MetaEntity#deliveryModeEnum 同口径）。 */
  public com.bone.metadata.catalog.domain.model.meta.MetaDeliveryMode deliveryModeEnum() {
    return com.bone.metadata.catalog.domain.model.meta.MetaDeliveryMode.fromCode(deliveryMode);
  }
}
