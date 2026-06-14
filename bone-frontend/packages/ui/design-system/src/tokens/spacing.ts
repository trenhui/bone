/**
 * Bone Design System — Spacing Tokens
 *
 * 基于 4px 基础单位（8pt grid）。
 * 与 Ant Design 5 内置间距语义对应，便于直接在组件 style prop 中引用。
 */
export const spacing = {
  /** 4px — 图标内边距、标签间距 */
  1: '4px',
  /** 8px — 行内元素间距、表单项小间距 */
  2: '8px',
  /** 12px — 组件内间距（button padding block） */
  3: '12px',
  /** 16px — 卡片内边距（小）、表格行高补充 */
  4: '16px',
  /** 20px — 弹窗内边距 */
  5: '20px',
  /** 24px — 卡片内边距（默认）、section 标题下间距 */
  6: '24px',
  /** 32px — 模块间距 */
  8: '32px',
  /** 48px — 页面级区块间距 */
  12: '48px',

  /** 语义化别名（兼容旧代码） */
  xs:   '4px',
  sm:   '8px',
  md:   '16px',
  lg:   '24px',
  xl:   '32px',
  '2xl': '48px',
  '3xl': '64px',
} as const;

export type Spacing = typeof spacing;
