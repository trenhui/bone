// @vitest-environment jsdom
import { describe, it, expect } from 'vitest';
import { resolveBroadcastTheme, claimAntdTheme } from './claimTheme';

/**
 * 理赔应用主题跟随 Shell 的回归钉子。
 *
 * <p>Shell 广播已解析的 light/dark；脏值/缺失一律回退 system（跟随 OS），
 * 绝不把非法值透传给 toAntdTheme 造成半亮半暗。
 */
describe('resolveBroadcastTheme', () => {
  it('Shell 广播的 light/dark 原样透传', () => {
    expect(resolveBroadcastTheme('light')).toBe('light');
    expect(resolveBroadcastTheme('dark')).toBe('dark');
  });

  it('缺失 / 脏值 / system 回退 system（跟随 OS），不透传脏值', () => {
    expect(resolveBroadcastTheme(undefined)).toBe('system');
    expect(resolveBroadcastTheme('system')).toBe('system');
    expect(resolveBroadcastTheme('LIGHT')).toBe('system');
    expect(resolveBroadcastTheme({})).toBe('system');
    expect(resolveBroadcastTheme(1)).toBe('system');
  });
});

describe('claimAntdTheme', () => {
  it('dark 生成带暗色算法的 antd 主题配置（不是 antd 默认亮色）', () => {
    const cfg = claimAntdTheme('dark');
    expect(cfg).toBeDefined();
    expect(cfg.token?.colorBgContainer).toBe('#1f1f1f');
    expect(cfg.token?.colorText).toBe('rgba(255,255,255,0.88)');
  });

  it('light 生成亮色主题配置', () => {
    const cfg = claimAntdTheme('light');
    expect(cfg.token?.colorBgContainer).toBe('#ffffff');
    expect(cfg.token?.colorPrimary).toBeDefined();
  });
});
