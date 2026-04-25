// 主题系统
import { designTokens } from '../tokens';

// 主题类型
export type Theme = 'light' | 'dark' | 'system';

// 主题配置
export interface ThemeConfig {
  colors: {
    background: string;
    surface: string;
    primary: string;
    secondary: string;
    text: {
      primary: string;
      secondary: string;
      disabled: string;
    };
    border: string;
    divider: string;
    semantic: {
      success: string;
      warning: string;
      error: string;
      info: string;
    };
  };
}

// 亮色主题
export const lightTheme: ThemeConfig = {
  colors: {
    background: designTokens.colors.gray[50],
    surface: '#ffffff',
    primary: designTokens.colors.primary[500],
    secondary: designTokens.colors.gray[600],
    text: {
      primary: designTokens.colors.gray[900],
      secondary: designTokens.colors.gray[600],
      disabled: designTokens.colors.gray[400],
    },
    border: designTokens.colors.gray[300],
    divider: designTokens.colors.gray[200],
    semantic: designTokens.colors.semantic,
  },
};

// 暗色主题
export const darkTheme: ThemeConfig = {
  colors: {
    background: '#121212',
    surface: '#1e1e1e',
    primary: designTokens.colors.primary[400],
    secondary: designTokens.colors.gray[400],
    text: {
      primary: designTokens.colors.gray[100],
      secondary: designTokens.colors.gray[400],
      disabled: designTokens.colors.gray[600],
    },
    border: designTokens.colors.gray[700],
    divider: designTokens.colors.gray[800],
    semantic: designTokens.colors.semantic,
  },
};

// 主题管理
export const getTheme = (theme: Theme): ThemeConfig => {
  if (theme === 'dark') {
    return darkTheme;
  }
  return lightTheme;
};

// 应用主题到全局
export const applyTheme = (theme: Theme) => {
  const themeConfig = getTheme(theme);
  const root = document.documentElement;
  
  // 设置 CSS 变量
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
  
  // 保存主题设置
  localStorage.setItem('bone-theme', theme);
};