import { describe, expect, it } from 'vitest';
import type { MetaField } from '../types';
import {
  businessKeyOf,
  isLabelLessEnum,
  isTechnicalKeyField,
  recordIdentityOf,
  visibleFieldsFor,
} from './persona';

/**
 * persona（消费面 / 实施面）判据单测。
 *
 * 判据性质：**消费面藏技术、实施面留全量**，且必须是可逆的（翻回 implement 什么都没丢）。
 * 另含两条变异探针（任务化：如果换lembangual implementation 再 Advantages).
 */
const field = (p: Partial<MetaField> & { code: string }): MetaField =>
  ({ id: p.code, entityId: 'e', name: p.code, displayName: p.displayName ?? p.code, type: 'STRING', ...p }) as MetaField;

describe('isTechnicalKeyField · 内部代理键判定', () => {
  it('整型 + _id 后缀 = 内部键（approver_id 实证）', () => {
    expect(isTechnicalKeyField(field({ code: 'approver_id', type: 'LONG' }))).toBe(true);
    expect(isTechnicalKeyField(field({ code: 'approver_id', type: 'INTEGER' }))).toBe(true);
  });

  it('负向探针：REFERENCE / 业务 Strings / 非 id 后缀一律不判为内部键', () => {
    expect(isTechnicalKeyField(field({ code: 'category_id', type: 'REFERENCE' }))).toBe(false);
    expect(isTechnicalKeyField(field({ code: 'claim_no', type: 'STRING' }))).toBe(false);
    expect(isTechnicalKeyField(field({ code: 'cost_center', type: 'STRING' }))).toBe(false);
    expect(isTechnicalKeyField(field({ code: 'amount', type: 'DECIMAL' }))).toBe(false);
    // 显式业务字段名即使巧合带 _id 语义也不隐藏
    expect(isTechnicalKeyField(field({ code: 'code', type: 'LONG' }))).toBe(false);
  });
});

describe('visibleFieldsFor · 双模式可逆', () => {
  const fields = [
    field({ code: 'claim_no', type: 'STRING' }),
    field({ code: 'amount', type: 'DECIMAL' }),
    field({ code: 'approver_id', type: 'LONG' }),
    field({ code: 'owner_id', type: 'LONG', required: true }),
  ];

  it('消费面剔除内部键，业务字段一字不少', () => {
    expect(visibleFieldsFor(fields, 'consume').map((f) => f.code)).toEqual([
      'claim_no',
      'amount',
      'owner_id',
    ]);
  });

  it('消费面永藏必填：不因展示分层制造写入缺列事故', () => {
    expect(visibleFieldsFor(fields, 'consume').some((f) => f.code === 'owner_id')).toBe(true);
  });

  it('实施面全量可见（信息未被删除，只是分层）', () => {
    expect(visibleFieldsFor(fields, 'implement').map((f) => f.code)).toEqual([
      'claim_no',
      'amount',
      'approver_id',
      'owner_id',
    ]);
  });
});

describe('businessKeyOf / recordIdentityOf · 不向业务用户暴露代理键', () => {
  const fields = [
    field({ code: 'claim_no', displayName: '报销单号', type: 'STRING', sortOrder: 1 }),
    field({ code: 'title', displayName: '报销事由', type: 'STRING', sortOrder: 2 }),
  ];

  it('取业务键（单号），而非雪花 ID', () => {
    const key = businessKeyOf({ id: 771066699203985400, claim_no: 'BX-2026-001' }, fields);
    expect(key).toEqual({ label: '报销单号', text: 'BX-2026-001' });
  });

  it('消费面文案不含 ID；实施面同时保留 ID 与业务键（排障需要）', () => {
    const rec = { id: 771066699203985400, claim_no: 'BX-2026-001' };
    expect(recordIdentityOf(rec, fields, 'consume')).toBe('报销单号：BX-2026-001');
    expect(recordIdentityOf(rec, fields, 'implement')).toContain('771066699203985400');
    expect(recordIdentityOf(rec, fields, 'implement')).toContain('BX-2026-001');
  });

  it('负向探针：无业务键字段时不退化成裸 ID', () => {
    expect(recordIdentityOf({ id: 123 }, [field({ code: 'amount', type: 'DECIMAL' })], 'consume')).toBe('该记录');
  });
});

describe('isLabelLessEnum · 建模缺口（缺中文标签）判定', () => {
  it('字符串数组形态（label===code）判为缺标签——状态列显示裸码的根因', () => {
    const f = field({ code: 'status', type: 'ENUM', enumValues: '["DRAFT","SUBMITTED","APPROVED"]' });
    expect(isLabelLessEnum(f)).toBe(true);
  });

  it('负向探针：已配中文 label 的枚举不算缺口', () => {
    const f = field({ code: 'status', type: 'ENUM', enumValues: '[{"code":"DRAFT","label":"草稿"}]' });
    expect(isLabelLessEnum(f)).toBe(false);
  });

  it('负向探针：非枚举字段、空枚举值都不误报', () => {
    expect(isLabelLessEnum(field({ code: 'title', type: 'STRING' }))).toBe(false);
    expect(isLabelLessEnum(field({ code: 'status', type: 'ENUM' }))).toBe(false);
  });
});
