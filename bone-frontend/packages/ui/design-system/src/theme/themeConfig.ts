import { designTokens } from '../tokens';

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
