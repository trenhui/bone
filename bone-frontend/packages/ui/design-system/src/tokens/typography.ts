// 排版令牌
export const typography = {
  fonts: {
    primary: 'Inter, system-ui, sans-serif',
    mono: 'JetBrains Mono, monospace',
  },
  scales: {
    xs: {
      fontSize: '0.75rem',
      lineHeight: 1.5,
    },
    sm: {
      fontSize: '0.875rem',
      lineHeight: 1.571,
    },
    md: {
      fontSize: '1rem',
      lineHeight: 1.6,
    },
    lg: {
      fontSize: '1.125rem',
      lineHeight: 1.667,
    },
    xl: {
      fontSize: '1.25rem',
      lineHeight: 1.7,
    },
    '2xl': {
      fontSize: '1.5rem',
      lineHeight: 1.8,
    },
  },
};

export type Typography = typeof typography;