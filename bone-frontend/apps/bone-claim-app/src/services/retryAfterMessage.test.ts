// @vitest-environment node
import { describe, it, expect, beforeAll } from 'vitest';
import { resolveErrorMessage, i18n } from '@bone/shared-utils';

/**
 * 429 Retry-After 秒数的用户可见文案判别力测试。
 *
 * 背景（联调 §15.4 发现）：createApiClient 把 Retry-After 透传到 error.retryAfter，
 * 但 resolveErrorMessage 只按 errorCode 取通用译文「请稍后重试」，秒数是死字段。
 * 本测试锁定：限流码 + 纯数字秒 → 带秒数译文；HTTP-date / 非限流码 → 保守回退通用译文；
 * 且中英文各自措辞（不在共享层硬编码中文后缀）。
 */
describe('限流错误提示消费 Retry-After', () => {
  beforeAll(async () => {
    await i18n.changeLanguage('zh-CN');
  });

  it('COMMON_RATE_LIMITED + 数字秒：文案带具体等待秒数', () => {
    const err = { errorCode: 'COMMON_RATE_LIMITED', retryAfter: '7' };
    const msg = resolveErrorMessage(err, '请求失败');
    expect(msg).toContain('7');
    expect(msg).toMatch(/秒/);
  });

  it('GW_RATE_LIMITED 网关限流码同样带秒数', () => {
    const err = { errorCode: 'GW_RATE_LIMITED', retryAfter: '30' };
    expect(resolveErrorMessage(err, '请求失败')).toContain('30');
  });

  it('Retry-After 为 HTTP-date：无法可靠换算，保守回退通用译文（不含秒数）', () => {
    const err = {
      errorCode: 'COMMON_RATE_LIMITED',
      retryAfter: 'Wed, 21 Oct 2026 07:28:00 GMT',
    };
    const msg = resolveErrorMessage(err, '请求失败');
    expect(msg).not.toMatch(/\d+\s*秒/);
    expect(msg).toContain('稍后重试');
  });

  it('非限流码即使带 retryAfter 也不追加秒数', () => {
    const err = { errorCode: 'COMMON_INTERNAL_ERROR', retryAfter: '7' };
    const msg = resolveErrorMessage(err, '系统错误');
    expect(msg).not.toContain('7');
    expect(msg).not.toMatch(/秒/);
  });

  it('限流码无 retryAfter：通用译文零回归', () => {
    expect(resolveErrorMessage({ errorCode: 'COMMON_RATE_LIMITED' }, '请求失败')).toContain(
      '稍后重试',
    );
  });

  it('英文语言：秒数文案走英文译文，不出现中文', async () => {
    await i18n.changeLanguage('en-US');
    try {
      const msg = resolveErrorMessage({ errorCode: 'COMMON_RATE_LIMITED', retryAfter: '7' }, 'failed');
      expect(msg).toContain('7');
      expect(msg).toMatch(/seconds/);
      expect(msg).not.toMatch(/[\u4e00-\u9fa5]/);
    } finally {
      await i18n.changeLanguage('zh-CN');
    }
  });

  it('异常秒数（0 / 超大 / 非数字）均不显示', () => {
    expect(resolveErrorMessage({ errorCode: 'COMMON_RATE_LIMITED', retryAfter: '0' }, '失败')).not.toContain('0 秒');
    expect(resolveErrorMessage({ errorCode: 'COMMON_RATE_LIMITED', retryAfter: '999999' }, '失败')).not.toContain('999999');
  });
});
