import { designTokens } from '../tokens';

export interface ThemeConfig {
  colors: {
    background: string;
    surface: string;
    primary: string;
    primaryHover: string;
    primaryActive: string;
    secondary: string;
    text: {
      primary: string;
      secondary: string;
      tertiary: string;
      disabled: string;
    };
    border: string;
    borderSecondary: string;
    divider: string;
    semantic: {
      success: string;
      successBg: string;
      warning: string;
      warningBg: string;
      error: string;
      errorBg: string;
      info: string;
      infoBg: string;
    };
  };
}

const t = designTokens;

export const lightTheme: ThemeConfig = {
  colors: {
    background:     t.colors.gray[150],   // #f5f5f5 — 页面背景
    surface:        '#ffffff',             // 卡片/容器表面
    primary:        t.colors.primary[500], // #1677ff
    primaryHover:   t.colors.primary[400], // #4096ff
    primaryActive:  t.colors.primary[600], // #0958d9
    secondary:      t.colors.gray[600],    // #595959
    text: {
      primary:   t.colors.gray[800],   // #262626
      secondary: t.colors.gray[600],   // #595959
      tertiary:  t.colors.gray[500],   // #8c8c8c
      disabled:  t.colors.gray[400],   // #bfbfbf
    },
    border:          t.colors.gray[300],  // #d9d9d9
    borderSecondary: t.colors.gray[200],  // #f0f0f0
    divider:         t.colors.gray[200],  // #f0f0f0
    semantic: {
      success:   t.colors.semantic.success,
      successBg: t.colors.semantic.successBg,
      warning:   t.colors.semantic.warning,
      warningBg: t.colors.semantic.warningBg,
      error:     t.colors.semantic.error,
      errorBg:   t.colors.semantic.errorBg,
      info:      t.colors.semantic.info,
      infoBg:    t.colors.semantic.infoBg,
    },
  },
};

export const darkTheme: ThemeConfig = {
  colors: {
    background:     t.colors.gray[950],   // #141414
    surface:        '#1f1f1f',
    primary:        t.colors.primary[400], // #4096ff（dark 下亮度更友好）
    primaryHover:   t.colors.primary[300], // #69b1ff
    primaryActive:  t.colors.primary[500], // #1677ff
    secondary:      t.colors.gray[400],    // #bfbfbf
    text: {
      primary:   'rgba(255,255,255,0.88)',
      secondary: 'rgba(255,255,255,0.65)',
      tertiary:  'rgba(255,255,255,0.45)',
      disabled:  'rgba(255,255,255,0.25)',
    },
    border:          '#424242',
    borderSecondary: '#303030',
    divider:         'rgba(255,255,255,0.08)',
    semantic: {
      success:   t.colors.semantic.success,
      successBg: 'rgba(82,196,26,0.12)',
      warning:   t.colors.semantic.warning,
      warningBg: 'rgba(250,140,22,0.12)',
      error:     t.colors.semantic.error,
      errorBg:   'rgba(255,77,79,0.12)',
      info:      t.colors.primary[400],
      infoBg:    'rgba(64,150,255,0.12)',
    },
  },
};
