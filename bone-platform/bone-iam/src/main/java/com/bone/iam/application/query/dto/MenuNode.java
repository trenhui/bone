package com.bone.iam.application.query.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class MenuNode {
  private String id;
  private String parentId;
  private String name;
  private String path;
  private String icon;
  private Integer order;
  private String permission;

  /**
   * 节点类型（与 {@code iam_menu.type} 一致）：0-目录 / 1-菜单 / 2-按钮。
   *
   * <p>2026-10-08 补齐：此前 {@code GET /menus/current} 不下发 type，前端无法区分「导航项」与 「按钮级权限点」，导致 IAM
   * 里维护好的按钮权限到不了浏览器 ⇒ 按钮要么全露给所有人、要么只能写死。 现在 type=2 的节点随菜单一起下发，前端据此构建按钮权限清单。
   */
  private Integer type;

  private List<MenuNode> children = new ArrayList<>();
}
