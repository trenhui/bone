import type { ThemeConfig } from 'antd';
import { theme as antdTheme } from 'antd';

import { designTokens } from '../tokens';
import { applyThemeCss } from './applyThemeCss';
import { resolveThemeMode } from './resolveThemeMode';
import type { Theme } from './types';

/** BONE 设计令牌 → Ant Design ConfigProvider.theme */
export function toAntdTheme(mode: Theme): ThemeConfig {
  const resolved = resolveThemeMode(mode);
  applyThemeCss(mode, resolved);
  const t = designTokens;

  return {
    algorithm: resolved === 'dark' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: {
      colorPrimary: resolved === 'dark' ? t.colors.primary[400] : t.colors.primary[500],
      colorSuccess: t.colors.semantic.success,
      colorWarning: t.colors.semantic.warning,
      colorError: t.colors.semantic.error,
      colorInfo: t.colors.semantic.info,
      fontFamily: t.typography.fonts.primary,
      fontSize: 16,
      borderRadius: 6,
    },
  };
}
