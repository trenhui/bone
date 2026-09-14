package com.bone.metadata.catalog.domain.model;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.exception.DomainException;
import com.bone.metadata.catalog.domain.enums.MetaDeliveryMode;
import com.bone.metadata.catalog.domain.enums.MetaEntityStatus;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Date;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("meta_entity")
public class MetaEntity extends AbstractEntity<Long> {

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "description")
  private String description;

  @Column(name = "table_name", nullable = false)
  private String tableName;

  @Column(name = "type", nullable = false)
  private Integer type;

  @Column(name = "delivery_mode", nullable = false)
  private Integer deliveryMode;

  @Column(name = "status", nullable = false)
  private Integer status;

  @Column(name = "module_id")
  private Long moduleId;

  @Column(name = "is_builtin", nullable = false)
  private Boolean builtin;

  @Column(name = "icon")
  private String icon;

  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder;

  @Column(name = "version", nullable = false)
  private Integer version;

  public static MetaEntity create(
      Long id,
      Long tenantId,
      String name,
      String code,
      String displayName,
      String description,
      String tableName,
      int entityType,
      int deliveryMode,
      String icon) {
    return create(
        id,
        tenantId,
        name,
        code,
        displayName,
        description,
        tableName,
        entityType,
        deliveryMode,
        icon,
        null);
  }

  public static MetaEntity create(
      Long id,
      Long tenantId,
      String name,
      String code,
      String displayName,
      String description,
      String tableName,
      int entityType,
      int deliveryMode,
      String icon,
      Long moduleId) {
    MetaEntity e = new MetaEntity();
    e.setId(id);
    e.tenantId = tenantId;
    e.name = name;
    e.code = code;
    e.displayName = displayName;
    e.description = description;
    e.tableName = tableName;
    e.type = entityType;
    e.deliveryMode = deliveryMode;
    e.status = MetaEntityStatus.DRAFT.getCode();
    e.builtin = false;
    e.sortOrder = 0;
    e.version = 0;
    e.icon = icon;
    e.moduleId = moduleId;
    Date now = new Date();
    e.setCreatedAt(now);
    e.setUpdatedAt(now);
    e.setDeleted(false);
    return e;
  }

  public void update(
      String name,
      String displayName,
      String description,
      String tableName,
      Integer sortOrder,
      String icon,
      Integer deliveryMode) {
    if (MetaEntityStatus.fromCode(this.status) == MetaEntityStatus.PUBLISHED) {
      throw new DomainException("已发布实体不可修改，请先归档或回退草稿");
    }
    this.name = name;
    this.displayName = displayName;
    this.description = description;
    this.tableName = tableName;
    if (sortOrder != null) {
      this.sortOrder = sortOrder;
    }
    this.icon = icon;
    if (deliveryMode != null) {
      this.deliveryMode = deliveryMode;
    }
    bumpVersion();
    this.setUpdatedAt(new Date());
  }

  public void publish() {
    if (MetaEntityStatus.fromCode(this.status) == MetaEntityStatus.PUBLISHED) {
      // 幂等：已发布实体再次发布视为「重新部署」，由调用方触发物理结构重新对齐，不抛异常
      return;
    }
    this.status = MetaEntityStatus.PUBLISHED.getCode();
    bumpVersion();
    this.setUpdatedAt(new Date());
  }

  private void bumpVersion() {
    this.version = (this.version == null ? 0 : this.version) + 1;
  }

  public MetaDeliveryMode deliveryModeEnum() {
    return MetaDeliveryMode.fromCode(deliveryMode);
  }

  public MetaEntityStatus statusEnum() {
    return MetaEntityStatus.fromCode(status);
  }
}
