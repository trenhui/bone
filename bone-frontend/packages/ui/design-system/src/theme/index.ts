import { applyThemeCss } from './applyThemeCss';
import { resolveThemeMode } from './resolveThemeMode';
import { darkTheme, lightTheme, type ThemeConfig } from './themeConfig';
import type { Theme } from './types';

export type { Theme } from './types';
export type { ThemeConfig } from './themeConfig';
export { lightTheme, darkTheme } from './themeConfig';
export { resolveThemeMode, readStoredTheme, publishThemeChange, BONE_THEME_STORAGE_KEY, BONE_THEME_CHANGE_EVENT } from './resolveThemeMode';
export { toAntdTheme } from './toAntdTheme';
export { applyThemeCss };

export const getTheme = (theme: Theme): ThemeConfig => {
  return resolveThemeMode(theme) === 'dark' ? darkTheme : lightTheme;
};

/** 应用主题：解析 system 后写入 CSS 变量并持久化偏好 */
export const applyTheme = (theme: Theme) => {
  const resolved = resolveThemeMode(theme);
  applyThemeCss(theme, resolved);
};
