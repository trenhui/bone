// 设计令牌索引文件
import { colors } from './colors';
import { typography } from './typography';
import { spacing } from './spacing';
import { breakpoints } from './breakpoints';
import { zIndex } from './zIndex';

export * from './colors';
export * from './typography';
export * from './spacing';
export * from './breakpoints';
export * from './zIndex';

// 导出完整的设计令牌
export const designTokens = {
  colors,
  typography,
  spacing,
  breakpoints,
  zIndex,
};

export type DesignTokens = typeof designTokens;