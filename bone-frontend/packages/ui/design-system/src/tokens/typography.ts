/**
 * Bone Design System — Typography Tokens
 *
 * 字体族对齐中文后台惯例（PingFang SC 优先）。
 * 字阶与 Ant Design 5 内置 typography scale 对应：
 *   - 基础正文 14px（AntD 默认 fontSize: 14）
 *   - 行高均保持 1.5714（= 22/14，AntD 默认行高）
 */
export const typography = {
  fonts: {
    /** 正文字体：优先 PingFang SC，覆盖中文后台场景 */
    primary: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif",
    /** 等宽字体：用于代码、ID、时间戳等 */
    mono: "'JetBrains Mono', 'Cascadia Code', Menlo, Monaco, Consolas, monospace",
  },

  /**
   * 字阶（对应 AntD 5 typographic scale）
   * 命名以实际 px 为准，避免歧义。
   */
  scales: {
    /** 12px — 辅助说明、时间戳、角标 */
    xs: {
      fontSize: '12px',
      lineHeight: 1.667,  // ~20px
    },
    /** 14px — 正文、表格内容（AntD 默认基准） */
    sm: {
      fontSize: '14px',
      lineHeight: 1.5714,  // 22px（AntD 默认）
    },
    /** 16px — 卡片标题、重要正文 */
    md: {
      fontSize: '16px',
      lineHeight: 1.5,     // 24px
    },
    /** 20px — 模块标题、Dialog 标题 */
    lg: {
      fontSize: '20px',
      lineHeight: 1.4,     // 28px
    },
    /** 24px — 页面标题 */
    xl: {
      fontSize: '24px',
      lineHeight: 1.3333,  // 32px
    },
    /** 28px — 数据大屏指标数字 */
    '2xl': {
      fontSize: '28px',
      lineHeight: 1.2857,  // 36px
    },
  },

  /** 字重语义化别名 */
  weights: {
    regular: '400',
    medium:  '500',
    semibold: '600',
    bold:    '700',
  },
} as const;

export type Typography = typeof typography;
