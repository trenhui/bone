/**
 * Bone Design System — Color Tokens
 *
 * 对齐 Ant Design 5.x 官方色板（2024 版本）。
 * primary 系列使用 AntD 生成式蓝 (#1677FF)，
 * semantic 语义色与 AntD ConfigProvider token 保持一致。
 */
export const colors = {
  /** 品牌主色：AntD 5 默认蓝 */
  primary: {
    50: '#e6f4ff',
    100: '#bae0ff',
    200: '#91caff',
    300: '#69b1ff',
    400: '#4096ff',  // hover
    500: '#1677ff',  // default (colorPrimary)
    600: '#0958d9',  // active
    700: '#003eb3',
    800: '#002c8c',
    900: '#001d66',
  },

  /** 中性灰：与 AntD text/border 色系对齐 */
  gray: {
    50:  '#ffffff',
    100: '#fafafa',  // table header bg
    150: '#f5f5f5',  // page bg
    200: '#f0f0f0',  // divider
    300: '#d9d9d9',  // border default
    400: '#bfbfbf',  // placeholder
    500: '#8c8c8c',  // text tertiary
    600: '#595959',  // text secondary
    700: '#434343',
    800: '#262626',  // text primary
    900: '#1f1f1f',
    950: '#141414',  // dark bg
  },

  /** 语义色：严格对齐 AntD 5 默认 token */
  semantic: {
    success:        '#52c41a',
    successBg:      '#f6ffed',
    successBorder:  '#b7eb8f',
    warning:        '#fa8c16',
    warningBg:      '#fff7e6',
    warningBorder:  '#ffd591',
    error:          '#ff4d4f',
    errorBg:        '#fff1f0',
    errorBorder:    '#ffa39e',
    info:           '#1677ff',
    infoBg:         '#e6f4ff',
    infoBorder:     '#91caff',
  },
};

export type Colors = typeof colors;
