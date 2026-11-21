// @vitest-environment node
import { describe, it, expect } from 'vitest';
import {
  analyticsWindow,
  decisionResultLabel,
  analyticsCountText,
  shadowRateText,
  durationText,
  reviewRateText,
  pushRateText,
  adoptionRateText,
  MAX_ANALYTICS_WINDOW_DAYS,
} from './analytics';

/**
 * 运营分析口径的判别力测试。
 *
 * 后端契约已存在但前端从未消费；接上时最容易犯的错是「把影子自动化率说成出金率」、
 * 「未知受控码臆造标签」「缺值显示 0」。这里钉住前端这三条纪律。
 */
describe('analyticsWindow · UTC 日界与时间窗上限', () => {
  it('天数被夹在 [1, 31]，与后端 MAX_WINDOW_DAYS 一致', () => {
    const now = Date.parse('2026-11-21T10:34:00Z');
    const over = analyticsWindow(999, now);
    const from = Date.parse(over.from);
    const to = Date.parse(over.to);
    expect((to - from) / 86400000).toBe(MAX_ANALYTICS_WINDOW_DAYS);
  });

  it('默认 7 天，且 to 对齐 UTC 次日零点（不受本地时区影响）', () => {
    const now = Date.parse('2026-11-21T10:34:00Z');
    const { from, to } = analyticsWindow(undefined, now);
    expect(to).toBe('2026-11-22T00:00:00Z');
    expect(from).toBe('2026-11-15T00:00:00Z');
  });

  it('非法天数回落为 1 天，不产生反向窗口', () => {
    const now = Date.parse('2026-11-21T10:34:00Z');
    const w = analyticsWindow(0, now);
    expect(Date.parse(w.to) - Date.parse(w.from)).toBe(86400000);
  });
});

describe('决策结论标签 · 受控码不臆造', () => {
  it('受控码映射为中文', () => {
    expect(decisionResultLabel('AUTO_APPROVE')).toContain('自动通过');
    expect(decisionResultLabel('MANUAL_REQUIRED')).toContain('转人工');
  });

  it('未知码降级显示原文，不伪装成已知标签', () => {
    expect(decisionResultLabel('BRAND_NEW_CODE')).toBe('BRAND_NEW_CODE');
  });
});

describe('指标缺值语义 · 不拿 0 冒充', () => {
  it('缺值/NaN 显示 —，显式 0 仍显示真实值', () => {
    for (const v of [null, undefined, NaN]) {
      expect(analyticsCountText(v)).toBe('—');
      expect(shadowRateText(v)).toBe('—');
      expect(durationText(v)).toBe('—');
    }
    expect(analyticsCountText(0)).toBe('0');
    expect(shadowRateText(0)).toBe('0.0%');
    expect(durationText(0)).toBe('0 ms');
  });

  it('影子率与耗时按口径格式化', () => {
    expect(shadowRateText(0.625)).toBe('62.5%');
    expect(durationText(1234)).toBe('1.23 s');
  });

  it('真机联调形态：Jackson long-as-string 不会被误判为缺值（mock 用数字会漏掉此缺陷）', () => {
    // 经网关 GET /analytics/summary 的真实返回：converged/fallback 是字符串，比率是数字。
    expect(analyticsCountText('31')).toBe('31');
    expect(analyticsCountText('0')).toBe('0');
    expect(shadowRateText(0.0)).toBe('0.0%');
    expect(analyticsCountText('not-a-number')).toBe('—');
    expect(analyticsCountText('')).toBe('—');
  });
});

describe('运营驾驶舱新增指标 · 方向与缺值判别力', () => {
  it('复核率：secondReviewedCount 为分子、reviewedCount 为分母，long-as-string 同样生效', () => {
    expect(reviewRateText(100, 30)).toBe('30.0%');
    expect(reviewRateText('100', '30')).toBe('30.0%');
    // 分母为 0 / 任一项缺值 → 无可计算样本，不写 0%。
    expect(reviewRateText(0, 0)).toBe('—');
    expect(reviewRateText(null, 30)).toBe('—');
    expect(reviewRateText(100, undefined)).toBe('—');
  });

  it('推送成功率：succeeded /（succeeded + failed）', () => {
    expect(pushRateText(80, 15)).toBe('84.2%');
    expect(pushRateText('80', '15')).toBe('84.2%');
    expect(pushRateText(0, 0)).toBe('—');
    expect(pushRateText(null, 15)).toBe('—');
  });

  it('AI 采纳率：adopts /（adopts + rejects + modifies）', () => {
    expect(adoptionRateText(6, 3, 1)).toBe('60.0%');
    expect(adoptionRateText(null, 3, 1)).toBe('—');
    expect(adoptionRateText(0, 0, 0)).toBe('—');
  });

  it('单案时长分位：毫秒/秒格式化与缺值（null 不得变成 0 ms）', () => {
    expect(durationText(950)).toBe('950 ms');
    expect(durationText(1049)).toBe('1.05 s');
    expect(durationText(null)).toBe('—');
    expect(durationText(undefined)).toBe('—');
  });
});
