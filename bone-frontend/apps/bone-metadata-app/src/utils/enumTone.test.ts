import { describe, expect, it } from 'vitest';
import { enumToneOf } from './enumTone';

/**
 * 语义色判据的判别力测试。
 *
 * <p>这类「查表 + 正则」的工具最容易**假绿**：全部断言都写成「随便命中一个颜色就算过」，
 * 那么把整张表删空也能过。所以这里刻意配三类断言：
 * ① 正向（该命中）；
 * ② **负向**（不该命中，必须回落 null）——证明判据有边界；
 * ③ 一致性（同 code 不同实体必须同色）——证明颜色不随选项下标漂移。
 */
describe('enumToneOf · 状态语义色', () => {
  it('正向：单据状态五态各得其所', () => {
    // 与平台 16 个 status 字段的既有措辞一致
    expect(enumToneOf({ code: 'DRAFT', label: '草稿' })).toBe('default');
    expect(enumToneOf({ code: 'SUBMITTED', label: '已提交' })).toBe('processing');
    expect(enumToneOf({ code: 'APPROVED', label: '已审批' })).toBe('success');
    expect(enumToneOf({ code: 'REJECTED', label: '已驳回' })).toBe('error');
    expect(enumToneOf({ code: 'PAID', label: '已打款' })).toBe('success');
  });

  it('一致性：同一状态码在不同实体必须同色（不受选项下标影响）', () => {
    const a = enumToneOf({ code: 'APPROVED', label: '已审批' });
    const b = enumToneOf({ code: 'APPROVED', label: '通过' });
    expect(a).toBe(b);
    // 反过来：若按「选项数组下标」取色，第 0 位与第 2 位必然不同色——这里必须相同
    expect(enumToneOf({ code: 'APPROVED', label: '已审批' })).toBe(enumToneOf({ code: 'PAID', label: '已打款' }));
  });

  it('中文化后只剩 label（code 缺失语义）仍能判定', () => {
    expect(enumToneOf({ code: 'A', label: '战略客户' })).toBeNull();
    expect(enumToneOf({ code: 'X1', label: '审批中' })).toBe('processing');
    expect(enumToneOf({ code: 'X2', label: '已过期' })).toBe('error');
    expect(enumToneOf({ code: 'X3', label: '挂起' })).toBe('warning');
  });

  it('负向探针：否定式不得被肯定式吞掉', () => {
    // 「未通过」含「通过」——若正则顺序写反就会误判成 success
    expect(enumToneOf({ code: 'UNKNOWN_1', label: '未通过' })).toBe('error');
    expect(enumToneOf({ code: 'UNKNOWN_2', label: '不通过' })).toBe('error');
    // 「已取消」含「已」与「取消」
    expect(enumToneOf({ code: 'UNKNOWN_3', label: '已取消' })).toBe('error');
  });

  it('负向探针：无状态语义的枚举一律返回 null（不硬凑颜色）', () => {
    expect(enumToneOf({ code: 'TRAVEL', label: '差旅' })).toBeNull();
    expect(enumToneOf({ code: 'B', label: '乙' })).toBeNull();
    expect(enumToneOf({ code: 'PLATINUM', label: '铂金会员' })).toBeNull();
    expect(enumToneOf(null)).toBeNull();
    expect(enumToneOf(undefined)).toBeNull();
  });

  it('负向探针：label 仍是裸码时不按 label 猜（避免把英文单词当中文语义）', () => {
    expect(enumToneOf({ code: 'WHATEVER', label: 'WHATEVER' })).toBeNull();
  });

  it('三态生命周期：已废弃选项不给注意力色', () => {
    expect(enumToneOf({ code: 'APPROVED', label: '已审批', state: 'DEPRECATED' })).toBe('default');
    expect(enumToneOf({ code: 'PAID', label: '已打款', state: 'DISABLED' })).toBe('default');
  });

  it('大小写与空白容错', () => {
    expect(enumToneOf({ code: ' approved ', label: '' })).toBe('success');
    expect(enumToneOf({ code: 'draft', label: 'DRAFT' })).toBe('default');
  });
});
