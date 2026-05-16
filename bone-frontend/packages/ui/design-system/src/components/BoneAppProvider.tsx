import React, { useEffect, useMemo, useState } from 'react';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';

import type { Theme } from '../theme/types';
import { toAntdTheme } from '../theme/toAntdTheme';
import {
  BONE_THEME_CHANGE_EVENT,
  publishThemeChange,
  readStoredTheme,
  resolveThemeMode,
} from '../theme/resolveThemeMode';

export interface BoneAppProviderProps {
  children: React.ReactNode;
  /** Shell 经 Qiankun props 下发；独立运行时读 localStorage */
  themeMode?: Theme;
}

export function BoneAppProvider({ children, themeMode }: BoneAppProviderProps) {
  const [mode, setMode] = useState<Theme>(() => themeMode ?? readStoredTheme());

  useEffect(() => {
    if (themeMode !== undefined) {
      setMode(themeMode);
    }
  }, [themeMode]);

  useEffect(() => {
    const onThemeChange = (event: Event) => {
      const custom = event as CustomEvent<{ themeMode: Theme }>;
      if (custom.detail?.themeMode) {
        setMode(custom.detail.themeMode);
      }
    };
    window.addEventListener(BONE_THEME_CHANGE_EVENT, onThemeChange);
    return () => window.removeEventListener(BONE_THEME_CHANGE_EVENT, onThemeChange);
  }, []);

  useEffect(() => {
    if (mode !== 'system') {
      return;
    }
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const onSystemChange = () => publishThemeChange('system');
    media.addEventListener('change', onSystemChange);
    return () => media.removeEventListener('change', onSystemChange);
  }, [mode]);

  const antdTheme = useMemo(() => toAntdTheme(mode), [mode]);
  const resolved = resolveThemeMode(mode);

  useEffect(() => {
    document.documentElement.setAttribute('data-bone-theme', resolved);
    document.body.className = resolved;
  }, [resolved]);

  return (
    <ConfigProvider locale={zhCN} theme={antdTheme}>
      {children}
    </ConfigProvider>
  );
}
