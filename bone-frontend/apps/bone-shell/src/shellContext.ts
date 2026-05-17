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

export interface ShellMenuItem {
  key: string;
  label: string;
  icon: ReactNode;
  path: string;
  enabled: boolean;
}

export const MenuConfigContext = createContext<{
  menuConfig: ShellMenuItem[];
  updateMenuConfig: (key: string, enabled: boolean) => void;
}>({
  menuConfig: [],
  updateMenuConfig: () => {},
});
