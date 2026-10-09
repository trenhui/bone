import { createContext, type ReactNode } from 'react';
import type { Theme } from '@bone/ui';

export const ThemeContext = createContext({
  theme: 'system' as Theme,
  resolvedTheme: 'light' as 'light' | 'dark',
  toggleTheme: () => {},
});

export const LayoutContext = createContext({
  layoutMode: 'side' as 'side' | 'top' | 'mix',
  toggleLayoutMode: () => {},
});

/**
 * Shell 前端菜单树节点。
 *
 * **可见性的唯一真源是 IAM**：`GET /api/v1/iam/menus/current` 已按「租户 + 当前角色权限码」
 * 双重过滤后才下发，前端不再自决谁能看什么（2026-10-08 改造）。
 * 此前 `enabled` 是一个纯本地开关，任何用户登录后都看到同一份全量菜单。
 */
export interface ShellMenuItem {
  /** 后端菜单 id（字符串化的雪花 ID）——用后端主键，前端不再自造语义 key。 */
  key: string;
  label: string;
  icon: ReactNode;
  /** 微前端路由 pathname（如 `/iam`）。 */
  path?: string;
  /** 微应用内 hash 路由（如 `/accounts`）。 */
  hash?: string;
  /** 后端绑定的权限码；仅用于兜底与诊断，过滤已在服务端完成。 */
  permission?: string;
  /** 后端节点类型 0-目录 1-菜单 2-按钮。 */
  type?: number;
  children?: ShellMenuItem[];
}

/** 菜单/权限刷新事件：Shell 重新拉取后广播，微应用据此重渲染按钮。 */
export const MENU_REFRESH_EVENT = 'bone:menu:refresh';
