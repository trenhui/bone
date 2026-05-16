import { useCallback } from 'react';

import type { Theme } from './types';
import { applyTheme } from './index';
import { publishThemeChange, readStoredTheme, resolveThemeMode } from './resolveThemeMode';

const THEME_CYCLE: Theme[] = ['system', 'light', 'dark'];

export function useBoneThemeController(initial?: Theme) {
  const preference = initial ?? readStoredTheme();

  const cycleTheme = useCallback((current: Theme): Theme => {
    const idx = THEME_CYCLE.indexOf(current);
    const next = THEME_CYCLE[(idx + 1) % THEME_CYCLE.length];
    applyTheme(next);
    publishThemeChange(next);
    return next;
  }, []);

  return {
    preference,
    resolved: resolveThemeMode(preference),
    cycleTheme,
    applyTheme,
    publishThemeChange,
  };
}

export function themePreferenceLabel(theme: Theme): string {
  if (theme === 'system') {
    return '跟随系统';
  }
  return theme === 'light' ? '浅色' : '深色';
}
