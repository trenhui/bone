package com.bone.metadata.engine.starter.platform.model;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;

/**
 * 只读映射 meta_field。
 *
 * <p><b>租户语义（重要）</b>：{@code meta_field} 是 per-tenant 表（ADR-0016：catalog 侧 {@code MetaEntity} /
 * {@code MetaField} 各自声明 {@code tenantId}，隔离由 CatalogTenantSupport + 仓储条件保证）。本类是<b>引擎侧的第二份
 * 映射</b>，此前继承非租户基类且不带 {@code tenantId} ⇒ SDK 的 {@code TableMetadata.isTenantScoped()} 为 false ⇒
 * {@code TenantFilterInjector} 直接 return，<b>引擎按租户执行元数据查询时读到的字段定义同样跨租户</b>。
 *
 * <p>CatalogTenantSupport 只作用于 server catalog 侧，覆盖不到本类，故此处必须显式声明租户字段。
 *
 * @see com.bone.metadata.catalog.domain.model.meta.MetaField catalog 侧同表映射（已声明 tenantId）
 */
@Table("meta_field")
public class PlatformMetaField extends TenantAbstractEntity<Long> {

  @Column(name = "entity_id", nullable = false)
  private Long entityId;

  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "type", nullable = false)
  private String type;

  @Column(name = "is_required", nullable = false)
  private Boolean required;

  @Column(name = "is_pk", nullable = false)
  private Boolean pk;

  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder;

  public Long getEntityId() {
    return entityId;
  }

  public String getCode() {
    return code;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getType() {
    return type;
  }

  public Boolean getRequired() {
    return required;
  }

  public Boolean getPk() {
    return pk;
  }

  public Integer getSortOrder() {
    return sortOrder;
  }
}
