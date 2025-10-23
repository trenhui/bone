import { designTokens } from '../tokens';

// 主题配置
export const themes = {
  light: {
    colors: {
      ...designTokens.colors,
      background: designTokens.colors.white,
      surface: designTokens.colors.gray[50],
      text: designTokens.colors.gray[900],
      textSecondary: designTokens.colors.gray[600],
      border: designTokens.colors.gray[200],
      divider: designTokens.colors.gray[100],
      semantic: {
        success: designTokens.colors.success[500],
        warning: designTokens.colors.warning[500],
        error: designTokens.colors.error[500],
        info: designTokens.colors.info[500]
      }
    },
    typography: designTokens.typography,
    spacing: designTokens.spacing,
    breakpoints: designTokens.breakpoints,
    zIndex: designTokens.zIndex
  },
  dark: {
    colors: {
      ...designTokens.colors,
      background: designTokens.colors.gray[900],
      surface: designTokens.colors.gray[800],
      text: designTokens.colors.white,
      textSecondary: designTokens.colors.gray[400],
      border: designTokens.colors.gray[700],
      divider: designTokens.colors.gray[800],
      semantic: {
        success: designTokens.colors.success[400],
        warning: designTokens.colors.warning[400],
        error: designTokens.colors.error[400],
        info: designTokens.colors.info[400]
      }
    },
    typography: designTokens.typography,
    spacing: designTokens.spacing,
    breakpoints: designTokens.breakpoints,
    zIndex: designTokens.zIndex
  },
  highContrast: {
    colors: {
      ...designTokens.colors,
      background: designTokens.colors.black,
      surface: designTokens.colors.gray[900],
      text: designTokens.colors.white,
      textSecondary: designTokens.colors.gray[300],
      border: designTokens.colors.white,
      divider: designTokens.colors.gray[700],
      semantic: {
        success: '#00ff00',
        warning: '#ffff00',
        error: '#ff0000',
        info: '#0055ff'
      }
    },
    typography: {
      ...designTokens.typography,
      scales: Object.fromEntries(
        Object.entries(designTokens.typography.scales).map(([key, value]) => [
          key,
          { ...value, fontWeight: 'bold' }
        ])
      )
    },
    spacing: designTokens.spacing,
    breakpoints: designTokens.breakpoints,
    zIndex: designTokens.zIndex
  }
};

// 生成CSS变量
function generateCSSVariables(theme: typeof themes.light): string {
  let cssVars = ':root {\n';

  // 递归处理嵌套对象
  function processObject(obj: any, prefix: string = '--'): void {
    for (const [key, value] of Object.entries(obj)) {
      // 处理数字键名，如颜色变体
      const safeKey = typeof key === 'string' && !isNaN(Number(key)) ? `-${key}` : key;
      const fullKey = `${prefix}${safeKey}`;

      if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
        processObject(value, `${fullKey}-`);
      } else {
        // 生成CSS变量
        cssVars += `  ${fullKey}: ${String(value)};\n`;
      }
    }
  }

  // 处理主题中的各个部分
  processObject(theme.colors, '--color-');
  processObject(theme.typography, '--typography-');
  processObject(theme.spacing, '--spacing-');
  processObject(theme.zIndex, '--z-');

  cssVars += '}';
  return cssVars;
}

// 应用主题
export function applyTheme(themeName: 'light' | 'dark' | 'highContrast'): void {
  const theme = themes[themeName];
  if (!theme) {
    console.error(`Theme ${themeName} not found`);
    return;
  }
  
  // 生成并应用CSS变量
  const cssVars = generateCSSVariables(theme);
  
  // 创建或更新style标签
  let styleElement = document.getElementById('bone-theme-variables') as HTMLStyleElement;
  if (!styleElement) {
    styleElement = document.createElement('style');
    styleElement.id = 'bone-theme-variables';
    document.head.appendChild(styleElement);
  }
  
  styleElement.textContent = cssVars;
  
  // 更新body类名以便于主题特定的CSS选择器
  document.body.className = document.body.className.replace(/theme-\w+/g, '');
  document.body.classList.add(`theme-${themeName}`);
  
  // 保存主题到localStorage
  try {
    localStorage.setItem('bone-theme', themeName);
  } catch (error) {
    console.warn('Failed to save theme to localStorage:', error);
  }
  
  // 触发主题变更事件
  window.dispatchEvent(new CustomEvent('bone-theme-changed', { detail: { themeName } }));
}

// 获取当前主题
export function getCurrentTheme(): 'light' | 'dark' | 'highContrast' {
  try {
    const savedTheme = localStorage.getItem('bone-theme') as 'light' | 'dark' | 'highContrast';
    if (savedTheme && themes[savedTheme]) {
      return savedTheme;
    }
  } catch (error) {
    console.warn('Failed to read theme from localStorage:', error);
  }
  
  // 根据系统偏好设置自动选择主题
  if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
    return 'dark';
  }
  
  return 'light';
}

// 初始化主题
export function initializeTheme(): void {
  const initialTheme = getCurrentTheme();
  applyTheme(initialTheme);
  
  // 监听系统主题变化
  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
  mediaQuery.addEventListener('change', (e) => {
    // 只有在用户没有明确设置主题时才响应系统变化
    try {
      if (!localStorage.getItem('bone-theme')) {
        applyTheme(e.matches ? 'dark' : 'light');
      }
    } catch (error) {
      // 如果localStorage不可用，总是响应系统变化
      applyTheme(e.matches ? 'dark' : 'light');
    }
  });
}

// 主题上下文类型
export interface ThemeContextType {
  currentTheme: 'light' | 'dark' | 'highContrast';
  setTheme: (theme: 'light' | 'dark' | 'highContrast') => void;
  isDark: boolean;
  isHighContrast: boolean;
}