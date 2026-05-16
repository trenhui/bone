import { darkTheme, lightTheme, type ThemeConfig } from './themeConfig';
import { BONE_THEME_STORAGE_KEY } from './resolveThemeMode';
import type { Theme } from './types';

function applyThemeConfig(themeConfig: ThemeConfig, resolved: 'light' | 'dark') {
  const root = document.documentElement;
  root.style.setProperty('--background', themeConfig.colors.background);
  root.style.setProperty('--surface', themeConfig.colors.surface);
  root.style.setProperty('--primary', themeConfig.colors.primary);
  root.style.setProperty('--secondary', themeConfig.colors.secondary);
  root.style.setProperty('--text-primary', themeConfig.colors.text.primary);
  root.style.setProperty('--text-secondary', themeConfig.colors.text.secondary);
  root.style.setProperty('--text-disabled', themeConfig.colors.text.disabled);
  root.style.setProperty('--border', themeConfig.colors.border);
  root.style.setProperty('--divider', themeConfig.colors.divider);
  root.style.setProperty('--success', themeConfig.colors.semantic.success);
  root.style.setProperty('--warning', themeConfig.colors.semantic.warning);
  root.style.setProperty('--error', themeConfig.colors.semantic.error);
  root.style.setProperty('--info', themeConfig.colors.semantic.info);
  root.setAttribute('data-bone-theme', resolved);
  document.body.className = resolved;
}

/** 将解析后的主题写入 CSS 变量并持久化用户偏好 */
export function applyThemeCss(theme: Theme, resolved: 'light' | 'dark') {
  const themeConfig = resolved === 'dark' ? darkTheme : lightTheme;
  applyThemeConfig(themeConfig, resolved);
  localStorage.setItem(BONE_THEME_STORAGE_KEY, theme);
}
