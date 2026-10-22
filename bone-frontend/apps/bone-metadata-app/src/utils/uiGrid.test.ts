import { describe, expect, it } from 'vitest';
import type { Control } from '@bone/ui-schema';
import { GRID_COLUMNS, gridItemStyle, isFullWidthControl, spanOf } from './uiGrid';

const ctrl = (props?: Record<string, unknown>, controlKind?: Control['controlKind']): Control => ({
  fieldCode: 'f1',
  ...(controlKind ? { controlKind } : {}),
  ...(props ? { props } : {}),
});

describe('spanOf（协议 props.span，24 栏制）', () => {
  it('缺省 → 半行 12（旧 schema 无 span 时保持双栏行为）', () => {
    expect(spanOf(ctrl())).toBe(12);
    expect(spanOf(ctrl({}))).toBe(12);
  });

  it('合法值原样返回', () => {
    expect(spanOf(ctrl({ span: 24 }))).toBe(24);
    expect(spanOf(ctrl({ span: 12 }))).toBe(12);
    expect(spanOf(ctrl({ span: 8 }))).toBe(8);
    expect(spanOf(ctrl({ span: 6 }))).toBe(6);
    expect(spanOf(ctrl({ span: 1 }))).toBe(1);
  });

  it('越界/非法值收敛为缺省 12（不猜渲染）', () => {
    expect(spanOf(ctrl({ span: 0 }))).toBe(12);
    expect(spanOf(ctrl({ span: 25 }))).toBe(12);
    expect(spanOf(ctrl({ span: -6 }))).toBe(12);
    expect(spanOf(ctrl({ span: 12.7 }))).toBe(12); // 向下取整
    expect(spanOf(ctrl({ span: '8' }))).toBe(12); // 字符串不入协议
    expect(spanOf(ctrl({ span: null }))).toBe(12);
  });
});

describe('isFullWidthControl（整行判定）', () => {
  it('span=24 整行', () => {
    expect(isFullWidthControl(ctrl({ span: 24 }))).toBe(true);
  });

  it('表格/附件/时间线恒整行（窄栏放不下）', () => {
    expect(isFullWidthControl(ctrl(undefined, 'CHILD_TABLE'))).toBe(true);
    expect(isFullWidthControl(ctrl(undefined, 'ATTACHMENT'))).toBe(true);
    expect(isFullWidthControl(ctrl(undefined, 'TIMELINE'))).toBe(true);
  });

  it('半行/无声明不整行', () => {
    expect(isFullWidthControl(ctrl({ span: 12 }))).toBe(false);
    expect(isFullWidthControl(ctrl())).toBe(false);
    expect(isFullWidthControl(ctrl(undefined, 'INPUT'))).toBe(false);
  });
});

describe('gridItemStyle（预览与运行时共用）', () => {
  it('整行 → 1 / -1', () => {
    expect(gridItemStyle(ctrl({ span: 24 }))).toEqual({ gridColumn: '1 / -1' });
    expect(gridItemStyle(ctrl(undefined, 'CHILD_TABLE'))).toEqual({ gridColumn: '1 / -1' });
  });

  it('非整行 → span N', () => {
    expect(gridItemStyle(ctrl({ span: 8 }))).toEqual({ gridColumn: 'span 8' });
    expect(gridItemStyle(ctrl({ span: 6 }))).toEqual({ gridColumn: 'span 6' });
    expect(gridItemStyle(ctrl())).toEqual({ gridColumn: 'span 12' }); // 缺省半行
  });

  it('GRID_COLUMNS 与协议 schema.json 的 span 上界一致', () => {
    expect(GRID_COLUMNS).toBe(24);
  });
});
