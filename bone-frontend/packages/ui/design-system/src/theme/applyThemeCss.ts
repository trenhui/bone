import { darkTheme, lightTheme, type ThemeConfig } from './themeConfig';
import { BONE_THEME_STORAGE_KEY } from './resolveThemeMode';
import type { Theme } from './types';

/**
 * 将 ThemeConfig 写入 CSS 自定义属性（Custom Properties），
 * 供不使用 AntD 组件的纯 CSS / CSS Modules 区域直接引用。
 *
 * 命名规则：--bone-<category>-<name>
 * 例：var(--bone-color-primary)、var(--bone-color-text-secondary)
 */
function applyThemeConfig(themeConfig: ThemeConfig, resolved: 'light' | 'dark') {
  const root = document.documentElement;
  const c = themeConfig.colors;

  // ── 品牌色 ──────────────────────────────────────────────────
  root.style.setProperty('--bone-color-primary',        c.primary);
  root.style.setProperty('--bone-color-primary-hover',  c.primaryHover);
  root.style.setProperty('--bone-color-primary-active', c.primaryActive);
  root.style.setProperty('--bone-color-secondary',      c.secondary);

  // ── 背景色 ──────────────────────────────────────────────────
  root.style.setProperty('--bone-color-bg-layout',   c.background);  // 页面背景
  root.style.setProperty('--bone-color-bg-container', c.surface);    // 容器/卡片

  // ── 文字色 ──────────────────────────────────────────────────
  root.style.setProperty('--bone-color-text',          c.text.primary);
  root.style.setProperty('--bone-color-text-secondary', c.text.secondary);
  root.style.setProperty('--bone-color-text-tertiary',  c.text.tertiary);
  root.style.setProperty('--bone-color-text-disabled',  c.text.disabled);

  // ── 边框色 ──────────────────────────────────────────────────
  root.style.setProperty('--bone-color-border',          c.border);
  root.style.setProperty('--bone-color-border-secondary', c.borderSecondary);
  root.style.setProperty('--bone-color-divider',          c.divider);

  // ── 语义色 ──────────────────────────────────────────────────
  root.style.setProperty('--bone-color-success',     c.semantic.success);
  root.style.setProperty('--bone-color-success-bg',  c.semantic.successBg);
  root.style.setProperty('--bone-color-warning',     c.semantic.warning);
  root.style.setProperty('--bone-color-warning-bg',  c.semantic.warningBg);
  root.style.setProperty('--bone-color-error',       c.semantic.error);
  root.style.setProperty('--bone-color-error-bg',    c.semantic.errorBg);
  root.style.setProperty('--bone-color-info',        c.semantic.info);
  root.style.setProperty('--bone-color-info-bg',     c.semantic.infoBg);

  // ── 旧版兼容别名（避免存量代码 break） ──────────────────────
  root.style.setProperty('--background',      c.background);
  root.style.setProperty('--surface',         c.surface);
  root.style.setProperty('--primary',         c.primary);
  root.style.setProperty('--secondary',       c.secondary);
  root.style.setProperty('--text-primary',    c.text.primary);
  root.style.setProperty('--text-secondary',  c.text.secondary);
  root.style.setProperty('--text-disabled',   c.text.disabled);
  root.style.setProperty('--border',          c.border);
  root.style.setProperty('--divider',         c.divider);
  root.style.setProperty('--success',         c.semantic.success);
  root.style.setProperty('--warning',         c.semantic.warning);
  root.style.setProperty('--error',           c.semantic.error);
  root.style.setProperty('--info',            c.semantic.info);

  // ── 主题标记 ─────────────────────────────────────────────────
  root.setAttribute('data-bone-theme', resolved);
  // 保留 body class，方便 CSS 选择器 .dark / .light 使用
  document.body.classList.remove('light', 'dark');
  document.body.classList.add(resolved);
}

/** 将解析后的主题写入 CSS 变量并持久化用户偏好 */
export function applyThemeCss(theme: Theme, resolved: 'light' | 'dark') {
  const themeConfig = resolved === 'dark' ? darkTheme : lightTheme;
  applyThemeConfig(themeConfig, resolved);
  localStorage.setItem(BONE_THEME_STORAGE_KEY, theme);
}
