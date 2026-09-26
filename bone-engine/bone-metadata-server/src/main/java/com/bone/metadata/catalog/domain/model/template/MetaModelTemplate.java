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

  /** 读侧重建（含 status）：仅 infrastructure/query 装配领域对象使用，不做校验性变更。 */
  public static MetaModelTemplate reconstitute(
      Long id,
      String code,
      String name,
      String description,
      String category,
      String currentVersion,
      int status) {
    MetaModelTemplate t = create(id, code, name, description, category, currentVersion);
    t.status = status;
    return t;
  }
}
