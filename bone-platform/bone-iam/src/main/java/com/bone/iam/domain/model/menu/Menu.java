package com.bone.iam.domain.model.menu;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_menu")
public class Menu extends TenantAggregateRoot<Long> {

  /**
   * 逻辑删除标记。
   *
   * <p><b>为何必须显式声明</b>：bone-metadata-sdk 的 {@code TableMetadata#isSoftDeletable()} 判据是 <b>实体内是否存在带
   * {@link com.bone.core.annotation.Deleted} 的字段</b>，与 DDL 有无 {@code deleted} 列无关。 {@code
   * TenantAggregateRoot} 只提供 {@code tenantId}，不像 {@code AbstractEntity} 那样自带该字段； 本类若不声明，{@code
   * Repository#deleteById} 会发出 {@code DELETE FROM} —— 删除即整行物理消失、不可审计不可恢复。
   *
   * <p>本表<b>无唯一索引</b>，故恢复软删不存在「同值无法重建」冲突（见soft-delete-declaration-baseline.json 的 {@code
   * _uk_conflict} 段）。
   */
  // 必须紧贴字段（与 Dept 同理）：门禁 DELETED_FIELD_RE 只看注解与字段相邻，
  // 中间夹注释块就会被判成「缺软删声明」。
  @Deleted private Boolean deleted = false;

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

  /**
   * 校验菜单路由 path。
   *
   * <p><b>为何必须在域内校验</b>：菜单 path 的约定是 {@code /<微应用>#/<子路由>}（见菜单与路由一致性门禁）， 前端 shell 按 {@code BASE +
   * path} 直接导航。缺前导 {@code /} 时拼出来的 URL 形如 {@code http://localhost:3000E2Exxx-3}， 浏览器判定为非法 URL ⇒
   * 该菜单<b>永远点不开</b>，且在菜单树里看起来与正常菜单无异 —— 是典型的「配了不生效」： 2026-10-23 全模块 UI 巡检实测，历史 E2E 数据残留的 4 条 {@code
   * E2E<ts>-3} 菜单因此让 60 个菜单的巡检挂了 4 个， 且报错落在探针侧（{@code Cannot navigate to invalid
   * URL}），从前端根本看不出是数据问题。
   *
   * <p><b>为何只校验前导斜杠、不强求 {@code #}</b>：裸 {@code /<app>} 会落到微应用默认页（重复入口，由 menu-single-entry
   * 门禁另案拦截），属「能打开但不理想」；而缺 {@code /} 是「根本打不开」。先拦死不可用的那一类， 避免一次性收紧把既有数据全打成非法。
   *
   * <p><b>为何允许空</b>：目录型节点（{@code type != 1}）本身不可导航，path 留空是合法形态。
   */
  private static String requireReachablePath(String path) {
    if (path == null || path.isBlank()) {
      return path;
    }
    if (!path.startsWith("/")) {
      throw new DomainException("菜单路由必须以 / 开头，当前值: " + path);
    }
    return path;
  }

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
    menu.path = requireReachablePath(path);
    menu.icon = icon;
    menu.orderNo = orderNo == null ? 0 : orderNo;
    menu.permission = permission;
    menu.type = type == null ? 1 : type;
    menu.setTenantId(tenantId);
    menu.createdAt = LocalDateTime.now();
    menu.updatedAt = LocalDateTime.now();
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
    this.path = requireReachablePath(path);
    this.icon = icon;
    this.orderNo = orderNo;
    this.permission = permission;
    this.type = type;
    this.updatedAt = LocalDateTime.now();
  }
}
