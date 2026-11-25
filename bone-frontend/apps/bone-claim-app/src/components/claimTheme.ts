/**
 * 理赔应用的 antd 主题解析：复用平台设计令牌，不另立一套颜色。
 *
 * <p>背景：Shell 经 `bone:theme:change` 广播 `{ theme, locale }`，其中 `theme` 是
 * **已解析的** `'dark' | 'light'`（见 shell App.tsx `resolveThemeMode(theme)` 后写入全局上下文）。
 * 本模块把该值映射到 `@bone/ui/theme` 的 `toAntdTheme`，让理赔子应用的 antd 控件跟随全局主题，
 * 而不是使用 antd 默认亮色。
 *
 * <p>为什么用 `toAntdTheme` 而不是自己写 token：颜色/字号/圆角是平台设计系统的单一真源，
 * 子应用复制一份会漂移（与本项目「同一语义两处配置源」纪律一致）。
 */
import type { ThemeConfig } from 'antd';
import { toAntdTheme } from '@bone/ui';
import type { Theme } from '@bone/ui';

/**
 * 把 Shell 广播的 theme 字段解析成设计系统接受的偏好值。
 *
 * <p>Shell 广播的是已解析的 light/dark；独立运行时收不到广播，回退 `'system'` 跟随 OS。
 * 未知值一律回退 'system'，绝不把脏值透传给 toAntdTheme 造成半亮半暗。
 */
export function resolveBroadcastTheme(raw: unknown): Theme {
  return raw === 'light' || raw === 'dark' ? raw : 'system';
}

/** 生成 antd ConfigProvider 可直接消费的 theme 配置。 */
export function claimAntdTheme(raw: unknown): ThemeConfig {
  return toAntdTheme(resolveBroadcastTheme(raw));
}
