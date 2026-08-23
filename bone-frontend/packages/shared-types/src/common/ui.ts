/**
 * 通用 UI 类型
 */
import type { ReactNode } from 'react';

/**
 * 导航菜单项
 * icon 放宽为 ReactNode | string，兼容 shell 与业务 app 的不同用法
 */
export interface MenuItem {
  key: string;
  label: string;
  icon?: ReactNode | string;
  path: string;
}
