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
