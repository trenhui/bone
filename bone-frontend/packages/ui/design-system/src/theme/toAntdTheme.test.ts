import { describe, expect, it } from 'vitest';

import { designTokens } from '../tokens';
import { toAntdTheme } from './toAntdTheme';

describe('toAntdTheme', () => {
  it('maps BONE primary token to Ant Design colorPrimary', () => {
    const config = toAntdTheme('light');
    expect(config.token?.colorPrimary).toBe(designTokens.colors.primary[500]);
    expect(config.token?.colorSuccess).toBe(designTokens.colors.semantic.success);
  });

  it('uses dark algorithm for dark mode', () => {
    const config = toAntdTheme('dark');
    expect(config.token?.colorPrimary).toBe(designTokens.colors.primary[400]);
  });
});
