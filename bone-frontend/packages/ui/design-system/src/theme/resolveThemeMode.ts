import type { Theme } from './types';

/** 将用户偏好（含 system）解析为实际亮/暗模式 */
export function resolveThemeMode(theme: Theme): 'light' | 'dark' {
  if (theme === 'system') {
    if (typeof window !== 'undefined' && window.matchMedia) {
      return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    return 'light';
  }
  return theme;
}

export const BONE_THEME_STORAGE_KEY = 'bone-theme';

export const BONE_THEME_CHANGE_EVENT = 'bone:theme-change';

export function readStoredTheme(): Theme {
  if (typeof window === 'undefined') {
    return 'system';
  }
  const stored = localStorage.getItem(BONE_THEME_STORAGE_KEY);
  if (stored === 'light' || stored === 'dark' || stored === 'system') {
    return stored;
  }
  return 'system';
}

export function publishThemeChange(themeMode: Theme): void {
  if (typeof window === 'undefined') {
    return;
  }
  window.dispatchEvent(
    new CustomEvent(BONE_THEME_CHANGE_EVENT, { detail: { themeMode } }),
  );
}
