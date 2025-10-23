// 颜色系统
export const colors = {
  primary: {
    50: '#f0f7ff',
    100: '#e6f0ff',
    200: '#b3d9ff',
    300: '#66b3ff',
    400: '#3399ff',
    500: '#007acc',
    600: '#005a9e',
    700: '#003d6e',
    800: '#001f3f',
    900: '#000d1a'
  },
  secondary: {
    50: '#f5f5f5',
    100: '#e6e6e6',
    200: '#cccccc',
    300: '#999999',
    400: '#666666',
    500: '#404040',
    600: '#333333',
    700: '#262626',
    800: '#1a1a1a',
    900: '#0d0d0d'
  },
  success: {
    50: '#f0fff4',
    100: '#c6f6d5',
    200: '#9ae6b4',
    300: '#68d391',
    400: '#48bb78',
    500: '#38a169',
    600: '#2f855a',
    700: '#276749',
    800: '#22543d',
    900: '#1c4532'
  },
  warning: {
    50: '#fffaf0',
    100: '#feebc8',
    200: '#fbd38d',
    300: '#f6ad55',
    400: '#ed8936',
    500: '#dd6b20',
    600: '#c05621',
    700: '#9c4221',
    800: '#7b341e',
    900: '#652b19'
  },
  error: {
    50: '#fff5f5',
    100: '#fed7d7',
    200: '#feb2b2',
    300: '#fc8181',
    400: '#f56565',
    500: '#e53e3e',
    600: '#c53030',
    700: '#9c2c2c',
    800: '#822727',
    900: '#63171b'
  },
  info: {
    50: '#ebf8ff',
    100: '#bee3f8',
    200: '#90cdf4',
    300: '#63b3ed',
    400: '#4299e1',
    500: '#3182ce',
    600: '#2b6cb0',
    700: '#2c5282',
    800: '#2a4365',
    900: '#1a365d'
  },
  gray: {
    50: '#f9fafb',
    100: '#f3f4f6',
    200: '#e5e7eb',
    300: '#d1d5db',
    400: '#9ca3af',
    500: '#6b7280',
    600: '#4b5563',
    700: '#374151',
    800: '#1f2937',
    900: '#111827'
  },
  white: '#ffffff',
  black: '#000000'
};

// 排版系统
export const typography = {
  fonts: {
    primary: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
    monospace: 'SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace'
  },
  scales: {
    xs: {
      fontSize: '0.75rem',
      lineHeight: '1rem',
      fontWeight: 400
    },
    sm: {
      fontSize: '0.875rem',
      lineHeight: '1.25rem',
      fontWeight: 400
    },
    md: {
      fontSize: '1rem',
      lineHeight: '1.5rem',
      fontWeight: 500
    },
    lg: {
      fontSize: '1.125rem',
      lineHeight: '1.75rem',
      fontWeight: 500
    },
    xl: {
      fontSize: '1.25rem',
      lineHeight: '1.75rem',
      fontWeight: 600
    },
    '2xl': {
      fontSize: '1.5rem',
      lineHeight: '2rem',
      fontWeight: 700
    },
    '3xl': {
      fontSize: '1.875rem',
      lineHeight: '2.25rem',
      fontWeight: 700
    },
    '4xl': {
      fontSize: '2.25rem',
      lineHeight: '2.5rem',
      fontWeight: 800
    }
  }
};

// 间距系统
export const spacing = {
  xs: '0.25rem',  // 4px
  sm: '0.5rem',   // 8px
  md: '1rem',     // 16px
  lg: '1.5rem',   // 24px
  xl: '2rem',     // 32px
  '2xl': '3rem',  // 48px
  '3xl': '4rem',  // 64px
  '4xl': '6rem',  // 96px
  '5xl': '8rem'   // 128px
};

// 断点系统
export const breakpoints = {
  sm: '640px',
  md: '768px',
  lg: '1024px',
  xl: '1280px',
  '2xl': '1536px'
};

// z-index 系统
export const zIndex = {
  base: 0,
  dropdown: 1000,
  sticky: 1100,
  fixed: 1200,
  modalBackdrop: 1300,
  modal: 1400,
  popover: 1500,
  tooltip: 1600,
  toast: 1700
};

// 边框圆角
export const borderRadius = {
  sm: '0.125rem',
  md: '0.375rem',
  lg: '0.5rem',
  xl: '0.75rem',
  '2xl': '1rem',
  full: '9999px'
};

// 阴影系统
export const boxShadow = {
  sm: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
  md: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
  lg: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)',
  xl: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
  '2xl': '0 25px 50px -12px rgba(0, 0, 0, 0.25)'
};

// 动画系统
export const animations = {
  duration: {
    fast: '150ms',
    normal: '250ms',
    slow: '350ms'
  },
  easing: {
    easeIn: 'cubic-bezier(0.4, 0, 1, 1)',
    easeOut: 'cubic-bezier(0, 0, 0.2, 1)',
    easeInOut: 'cubic-bezier(0.4, 0, 0.2, 1)'
  }
};

// 完整的设计令牌
export const designTokens = {
  colors,
  typography,
  spacing,
  breakpoints,
  zIndex,
  borderRadius,
  boxShadow,
  animations
};