import { describe, expect, it } from 'vitest';
import { readinessText, verdictColor, verdictTextOrRaw } from './readinessView';

/** 反假绿：未知判词显示原始码、失败显示无法确认、false 不渲染为达标。 */
describe('就绪度判词前端映射', () => {
  it('AVAILABLE 才显示可判定可用', () => {
    expect(readinessText('AVAILABLE')).toBe('可判定可用');
    expect(verdictColor('AVAILABLE')).toBe('green');
  });

  it('DEGRADED 显示受限，不得显示可用', () => {
    expect(readinessText('DEGRADED')).toBe('受限可用');
    expect(readinessText('DEGRADED')).not.toBe('可判定可用');
  });

  it('UNKNOWN 显示无法确认且保留原始码', () => {
    expect(readinessText('UNKNOWN')).toBe('无法确认');
    expect(verdictTextOrRaw('UNKNOWN')).toContain('UNKNOWN');
  });

  it('未知判词原样显示，不乐观映射', () => {
    expect(readinessText('FUTURE_VERDICT')).toBe('无法确认');
    expect(verdictTextOrRaw('FUTURE_VERDICT')).toContain('FUTURE_VERDICT');
  });

  it('null/空判词 fail-closed', () => {
    expect(readinessText(null)).toBe('无法确认');
    expect(verdictTextOrRaw(undefined)).toBe('无法确认');
  });
});
