import { describe, expect, it, beforeEach } from 'vitest';
import { getLastPublish, isDirtySincePublish, lastPublishKey, setLastPublish } from './publishState';

/**
 * 发布脏态判定单测（融合方案 §3.2 脏态算法 MVP 版本）。
 *
 * 判据：实体 status=1（已发布）且 updatedAt 晚于最近一次本地发布时刻 ⇒ 待重新发布。
 * 这里覆盖三类边界：空值兜底、时钟/字符串解析、跨发布重置。
 */
describe('publishState', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  describe('isDirtySincePublish', () => {
    it('updatedAt 缺失时视为未脏（无依据不误报）', () => {
      expect(isDirtySincePublish(undefined, Date.now())).toBe(false);
      expect(isDirtySincePublish('', Date.now())).toBe(false);
    });

    it('数字时间戳晚于发布时刻 ⇒ 脏', () => {
      const published = 1_700_000_000_000;
      expect(isDirtySincePublish(published + 5_000, published)).toBe(true);
    });

    it('updatedAt 等于发布时刻 ⇒ 不脏（同一时刻不算更新）', () => {
      const t = 1_700_000_000_000;
      expect(isDirtySincePublish(t, t)).toBe(false);
    });

    it('updatedAt 早于发布时刻 ⇒ 不脏（发布后无结构变更）', () => {
      const published = 1_700_000_000_000;
      expect(isDirtySincePublish(published - 10_000, published)).toBe(false);
    });

    it('ISO 字符串与数字时间戳可等价解析', () => {
      const published = 1_700_000_000_000;
      expect(isDirtySincePublish(new Date(published + 1000).toISOString(), published)).toBe(true);
      expect(isDirtySincePublish(new Date(published - 1000).toISOString(), published)).toBe(false);
    });

    it('非法日期字符串不抛错且视为未脏', () => {
      expect(isDirtySincePublish('not-a-date', 0)).toBe(false);
    });

    // 以下为「防御分支契约」用例：NaN / <=0 兜底在纯 > 比较下多数输入结果相同（等价变异），
    // 但下列取值明确锁定了「epoch 0 / null / 空串一律判未脏」的契约——
    // 后续若重构摘掉兜底，这些用例将先行暴露语义漂移。
    it('epoch 0 时间戳（1970）判为未脏', () => {
      expect(isDirtySincePublish(0, 0)).toBe(false);
      expect(isDirtySincePublish('1970-01-01T00:00:00.000Z', 0)).toBe(false);
    });

    it('null / 空串等脏输入一律判为未脏', () => {
      expect(isDirtySincePublish(null as unknown as string, 0)).toBe(false);
      expect(isDirtySincePublish('   ', Date.now())).toBe(false);
    });

    it('从未发布过（lastPublish=0）且已更新时间戳 ⇒ 脏', () => {
      expect(isDirtySincePublish(Date.now(), 0)).toBe(true);
    });
  });

  describe('lastPublish 本地读写', () => {
    it('setLastPublish 后 getLastPublish 返回相同时刻', () => {
      setLastPublish('ent-1');
      expect(getLastPublish('ent-1')).toBeGreaterThan(0);
      expect(Number.isNaN(getLastPublish('ent-1'))).toBe(false);
    });

    it('未发布实体返回 0（而非 NaN，避免脏态判定被污染）', () => {
      expect(getLastPublish('never-published')).toBe(0);
    });

    it('不同实体 key 互不干扰', () => {
      setLastPublish('ent-a');
      const a = getLastPublish('ent-a');
      setLastPublish('ent-b');
      expect(getLastPublish('ent-a')).toBe(a);
      expect(getLastPublish('ent-b')).toBeGreaterThanOrEqual(a);
    });

    it('key 按实体维度隔离', () => {
      expect(lastPublishKey('x')).toBe('bone.meta.lastPublish.x');
      expect(lastPublishKey('x')).not.toBe(lastPublishKey('y'));
    });

    it('发布后脏态归零（闭环：发布→不再提示待重新发布）', () => {
      const entityUpdatedAt = Date.now();
      // 发布前：发布时刻为 0 ⇒ 判脏
      expect(isDirtySincePublish(entityUpdatedAt, getLastPublish('ent-1'))).toBe(true);
      // 发布后：记录时刻晚于/等于 updatedAt ⇒ 不脏
      setLastPublish('ent-1');
      expect(isDirtySincePublish(entityUpdatedAt, getLastPublish('ent-1'))).toBe(false);
    });
  });
});
