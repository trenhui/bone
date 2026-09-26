import React, { useEffect, useMemo, useState } from 'react';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';
// i18n 方案 §5.5.2「A 方案」：locale 与 theme 一样经 core-event-bus 单源下发。
// 新增这两条包依赖边（@bone/ui → @bone/core-event-bus / @bone/shared-utils）属 L3，已在方案中登记。
import { globalEventBus } from '@bone/core-event-bus';
import { currentLocale, i18n, type SupportedLanguage } from '@bone/shared-utils';

import type { Theme } from '../theme/types';
import { toAntdTheme } from '../theme/toAntdTheme';
import {
  BONE_THEME_CHANGE_EVENT,
  publishThemeChange,
  readStoredTheme,
  resolveThemeMode,
} from '../theme/resolveThemeMode';

/**
 * ★★ 总线实例必须优先取 Shell 挂到 window 上的那一个。
 *
 * `globalEventBus` 是 `micro-app-messenger.ts` 的**模块级** `new SimpleEventBus()`，没有 window 兜底单例；
 * 8 个应用各自独立打包，`@bone/ui` 在微应用 bundle 里 `import` 到的是**另一份实例**，
 * 与 Shell emit 的那个不是同一个对象 → 语言切换事件永远收不到（Shell 自身 bundle 内二者相同，所以这个坑只在微应用暴露）。
 * 写法与 `bone-iam-app/src/main.tsx` 保持一致。
 */
const resolveEventBus = (): typeof globalEventBus =>
  (window as unknown as { __BONE_EVENT_BUS__?: typeof globalEventBus }).__BONE_EVENT_BUS__ ??
  globalEventBus;

export interface BoneAppProviderProps {
  children: React.ReactNode;
  /** Shell 经 Qiankun props 下发；独立运行时读 localStorage */
  themeMode?: Theme;
  /** Shell 经 Qiankun props 下发；独立运行时回退到 i18next 的 LanguageDetector 结果 */
  locale?: SupportedLanguage;
}

export function BoneAppProvider({
  children,
  themeMode,
  locale: localeProp,
}: BoneAppProviderProps) {
  const [mode, setMode] = useState<Theme>(() => themeMode ?? readStoredTheme());

  // locale 初始化优先级：Shell 下发 prop > i18next LanguageDetector 的检测结果
  // （LanguageDetector 已按 querystring > cookie > localStorage > navigator 检测完毕）。
  // ★ 组件内不重复读 localStorage / navigator——避免与 i18next 判成两个值导致
  //   「AntD 组件是英文、页面文案是中文」的中英混排。
  const [locale, setLocale] = useState<SupportedLanguage>(
    () => localeProp ?? currentLocale()
  );

  useEffect(() => {
    if (themeMode !== undefined) {
      setMode(themeMode);
    }
  }, [themeMode]);

  useEffect(() => {
    if (localeProp !== undefined) {
      setLocale(localeProp);
      if (i18n.language !== localeProp) {
        void i18n.changeLanguage(localeProp);
      }
    }
  }, [localeProp]);

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

  // 监听 Shell 经 core-event-bus 广播的语言切换（与 theme 的原生 CustomEvent 通道并存：
  // 原生通道保留为 design-system 独立运行时的退化路径，二者不互相替代）
  useEffect(() => {
    const bus = resolveEventBus();
    const onLocaleChange = (data: { theme?: string; locale?: SupportedLanguage }) => {
      if (data?.locale) {
        setLocale(data.locale);
        if (i18n.language !== data.locale) {
          void i18n.changeLanguage(data.locale);
        }
      }
    };
    bus.on('bone:theme:change', onLocaleChange);
    // SimpleEventBus.on() 无返回值，必须显式 off；且**必须传 handler**——
    // `off(event)` 不带 handler 会清空该事件的全部监听者（同应用里别人的订阅会被一起摘掉）。
    return () => bus.off('bone:theme:change', onLocaleChange);
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

  const antdLocale = locale === 'en-US' ? enUS : zhCN;

  return (
    <ConfigProvider locale={antdLocale} theme={antdTheme}>
      {/* antd v5：静态 message.error() 不继承 ConfigProvider 的 locale/theme，
          必须经 <App> 上下文（App.useApp()）取实例 */}
      <App>{children}</App>
    </ConfigProvider>
  );
}
