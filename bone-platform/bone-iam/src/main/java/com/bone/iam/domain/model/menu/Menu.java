package com.bone.iam.domain.model.menu;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.iam.domain.model.menu.event.MenuCreatedEvent;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_menu")
public class Menu extends TenantAggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private String name;
  private Long parentId;
  private String path;
  private String icon;
  private Integer orderNo;
  private String permission;
  private Integer type;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static Menu create(
      String name,
      Long parentId,
      String path,
      String icon,
      Integer orderNo,
      String permission,
      Integer type,
      Long tenantId) {
    Menu menu = new Menu();
    menu.name = name;
    menu.parentId = parentId;
    menu.path = path;
    menu.icon = icon;
    menu.orderNo = orderNo == null ? 0 : orderNo;
    menu.permission = permission;
    menu.type = type == null ? 1 : type;
    menu.setTenantId(tenantId);
    menu.createdAt = LocalDateTime.now();
    menu.updatedAt = LocalDateTime.now();
    menu.addDomainEvent(new MenuCreatedEvent(menu));
    return menu;
  }

  public void update(
      String name,
      Long parentId,
      String path,
      String icon,
      Integer orderNo,
      String permission,
      Integer type) {
    this.name = name;
    this.parentId = parentId;
    this.path = path;
    this.icon = icon;
    this.orderNo = orderNo;
    this.permission = permission;
    this.type = type;
    this.updatedAt = LocalDateTime.now();
  }
}
