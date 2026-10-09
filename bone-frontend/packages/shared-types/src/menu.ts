/**
 * 动态菜单类型（Shell 侧）。
 *
 * 约定：IAM `GET /api/v1/iam/menu/current` 返回「当前用户可见」的菜单树
 * （后端已完成角色过滤）。前端仅做渲染 + 本地 fallback，不做越权判定
 * （后端 @PreAuthorize 兜底）。
 *
 * 该契约由 iam-org-menu-baseline 最终落地；在本 change 中先定义类型，
 * Shell 侧基于 fallback + 真实调用切换。
 */

/** 单条菜单节点（与后端 MenuVO 对齐） */
export interface MenuNode {
  /** 菜单唯一 ID */
  id: string;
  /** 父节点 ID，根节点为 null 或空串 */
  parentId: string | null;
  /** 菜单名称（展示文案） */
  name: string;
  /** 路由路径（与微应用注册 key 对齐，如 /iam、/metadata） */
  path: string;
  /** 图标名（antd icon 名，可选） */
  icon?: string;
  /** 排序权重，前端按从小到大渲染 */
  order?: number;
  /** 可见所需的权限码；为空表示所有已登录用户可见 */
  permission?: string;
  /**
   * 节点类型，与 `iam_menu.type` / 后端 `MenuNode` 一致：0-目录 1-菜单 2-按钮。
   *
   * 2026-10-08 补齐：此前 `/menus/current` 的类型定义里没有这个字段，前端拿到的
   * type=2「按钮权限点」无法被识别，只能混在导航树里或干脆丢弃 ⇒ 按钮没法跟着 IAM
   * 的角色授权走。补齐后 Shell 可据此把按钮权限点单独下发（见 App.tsx 的 actionCodes）。
   */
  type?: number;
  /** 子菜单 */
  children?: MenuNode[];
}

/** 当前用户菜单响应（ApiResponse<MenuNode[]> 的 data） */
export type MenuList = MenuNode[];

/** 通知未读计数响应 */
export interface NotificationSummary {
  /** 未读消息数 */
  unread: number;
}
