import type { ThemeConfig } from 'antd';
import { theme as antdTheme } from 'antd';

import { designTokens } from '../tokens';
import { applyThemeCss } from './applyThemeCss';
import { resolveThemeMode } from './resolveThemeMode';
import type { Theme } from './types';

/**
 * 将 Bone 设计令牌映射为 Ant Design 5 ConfigProvider.theme。
 *
 * 设计原则：
 * - 主色使用 AntD 5 官方蓝 #1677FF，保证与 AntD 组件默认视觉完全一致
 * - 语义色（success/warning/error/info）严格对齐 AntD 5 默认值
 * - 字体优先 PingFang SC，兼顾中文后台场景
 * - 基础字号 14px（AntD 后台标准），行高 1.5714（22px）
 * - 圆角 6px（按钮/输入框），与 AntD 5 默认一致
 */
export function toAntdTheme(mode: Theme): ThemeConfig {
  const resolved = resolveThemeMode(mode);
  applyThemeCss(mode, resolved);
  const t = designTokens;

  const isDark = resolved === 'dark';

  return {
    algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,

    token: {
      // ── 主色 ────────────────────────────────────────────────
      colorPrimary:     isDark ? t.colors.primary[400] : t.colors.primary[500],
      colorPrimaryHover: isDark ? t.colors.primary[300] : t.colors.primary[400],
      colorPrimaryActive: isDark ? t.colors.primary[500] : t.colors.primary[600],

      // ── 语义色 ───────────────────────────────────────────────
      colorSuccess:     t.colors.semantic.success,
      colorSuccessBg:   t.colors.semantic.successBg,
      colorSuccessBorder: t.colors.semantic.successBorder,
      colorWarning:     t.colors.semantic.warning,
      colorWarningBg:   t.colors.semantic.warningBg,
      colorWarningBorder: t.colors.semantic.warningBorder,
      colorError:       t.colors.semantic.error,
      colorErrorBg:     t.colors.semantic.errorBg,
      colorErrorBorder: t.colors.semantic.errorBorder,
      colorInfo:        t.colors.semantic.info,
      colorInfoBg:      t.colors.semantic.infoBg,
      colorInfoBorder:  t.colors.semantic.infoBorder,

      // ── 文字色 ───────────────────────────────────────────────
      colorText:          isDark ? 'rgba(255,255,255,0.88)' : t.colors.gray[800],      // #262626
      colorTextSecondary: isDark ? 'rgba(255,255,255,0.65)' : t.colors.gray[600],      // #595959
      colorTextTertiary:  isDark ? 'rgba(255,255,255,0.45)' : t.colors.gray[500],      // #8c8c8c
      colorTextQuaternary: isDark ? 'rgba(255,255,255,0.25)' : t.colors.gray[400],     // #bfbfbf
      colorTextPlaceholder: isDark ? 'rgba(255,255,255,0.25)' : t.colors.gray[400],

      // ── 背景色 ───────────────────────────────────────────────
      colorBgContainer:   isDark ? '#1f1f1f' : '#ffffff',
      colorBgElevated:    isDark ? '#2a2a2a' : '#ffffff',
      colorBgLayout:      isDark ? t.colors.gray[950] : t.colors.gray[150],          // #f5f5f5
      colorBgSpotlight:   isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.04)',
      colorFill:          isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.04)',
      colorFillSecondary: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.02)',

      // ── 边框色 ───────────────────────────────────────────────
      colorBorder:       isDark ? '#424242' : t.colors.gray[300],   // #d9d9d9
      colorBorderSecondary: isDark ? '#303030' : t.colors.gray[200], // #f0f0f0
      colorSplit:        isDark ? 'rgba(255,255,255,0.08)' : 'rgba(5,5,5,0.06)',

      // ── 排版 ─────────────────────────────────────────────────
      fontFamily:   t.typography.fonts.primary,
      fontFamilyCode: t.typography.fonts.mono,
      fontSize:     14,    // AntD 后台标准基础字号
      fontSizeSM:   12,
      fontSizeLG:   16,
      fontSizeXL:   20,
      fontSizeHeading1: 38,
      fontSizeHeading2: 30,
      fontSizeHeading3: 24,
      fontSizeHeading4: 20,
      fontSizeHeading5: 16,
      lineHeight:   1.5714,  // 22/14，AntD 默认
      lineHeightSM: 1.6667,
      lineHeightLG: 1.5,

      // ── 圆角 ─────────────────────────────────────────────────
      borderRadius:   6,    // 按钮、输入框
      borderRadiusSM: 4,    // 小尺寸组件、Tag
      borderRadiusLG: 8,    // 卡片、Modal、Popover
      borderRadiusXS: 2,

      // ── 阴影（高度感） ─────────────────────────────────────────
      boxShadow:       '0 1px 2px 0 rgba(0,0,0,0.03), 0 1px 6px -1px rgba(0,0,0,0.02), 0 2px 4px 0 rgba(0,0,0,0.02)',
      boxShadowSecondary: '0 6px 16px 0 rgba(0,0,0,0.08), 0 3px 6px -4px rgba(0,0,0,0.12), 0 9px 28px 8px rgba(0,0,0,0.05)',

      // ── 尺寸基准 ──────────────────────────────────────────────
      controlHeight:   32,   // 默认控件高度
      controlHeightSM: 24,
      controlHeightLG: 40,

      // ── 动画 ─────────────────────────────────────────────────
      motionDurationFast:   '0.1s',
      motionDurationMid:    '0.2s',
      motionDurationSlow:   '0.3s',
      motionEaseInOut:      'cubic-bezier(0.645, 0.045, 0.355, 1)',
      motionEaseOut:        'cubic-bezier(0.215, 0.61, 0.355, 1)',
    },

    components: {
      // ── Table ──────────────────────────────────────────────
      Table: {
        headerBg:         isDark ? '#1d1d1d' : t.colors.gray[100],   // #fafafa
        headerColor:      isDark ? 'rgba(255,255,255,0.88)' : t.colors.gray[800],
        headerSortActiveBg: isDark ? '#262626' : '#f0f0f0',
        rowHoverBg:       isDark ? 'rgba(255,255,255,0.04)' : t.colors.gray[100],
        borderColor:      isDark ? '#303030' : t.colors.gray[200],   // #f0f0f0
        cellPaddingBlock: 11,
        cellPaddingInline: 12,
        fontSize:         13,
      },

      // ── Form ───────────────────────────────────────────────
      Form: {
        labelFontSize:    13,
        itemMarginBottom: 20,
        verticalLabelPadding: '0 0 4px',
      },

      // ── Input ──────────────────────────────────────────────
      Input: {
        paddingBlock:   5,
        paddingInline:  11,
        addonBg:        isDark ? '#1d1d1d' : t.colors.gray[100],
      },

      // ── Button ─────────────────────────────────────────────
      Button: {
        paddingInline:        16,
        paddingInlineSM:      8,
        paddingInlineLG:      24,
        primaryShadow:        'none',   // 去掉 AntD 5 默认按钮阴影
        defaultShadow:        'none',
        dangerShadow:         'none',
      },

      // ── Modal ──────────────────────────────────────────────
      Modal: {
        titleFontSize:    16,
        titleLineHeight:  1.5,
        headerBg:         isDark ? '#1f1f1f' : '#ffffff',
        contentBg:        isDark ? '#1f1f1f' : '#ffffff',
        footerBg:         isDark ? '#1f1f1f' : '#ffffff',
        paddingMD:        20,
        paddingContentHorizontalLG: 24,
      },

      // ── Card ───────────────────────────────────────────────
      Card: {
        headerBg:       'transparent',
        paddingLG:      24,
      },

      // ── Menu ───────────────────────────────────────────────
      Menu: {
        itemHeight:         40,
        subMenuItemBg:      'transparent',
        itemSelectedColor:  isDark ? t.colors.primary[400] : t.colors.primary[500],
        itemSelectedBg:     isDark ? 'rgba(22,119,255,0.15)' : t.colors.primary[50],
        itemHoverBg:        isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)',
      },
    },
  };
}
