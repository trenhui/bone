import { describe, expect, it } from 'vitest';
import { parseEnumValues } from '@bone/shared-types';
import type { EnumOption } from '@bone/shared-types';

/**
 * 三态生命周期（界面设计器字典打通 v1.1 §4.5）的前端口径单测：
 * parseEnumValues 对 DEPRECATED 标记的解析 + 渲染/写校验两种过滤口径。
 *
 * 判据性质（而非结果）：① DEPRECATED 项必须带 state 存活到快照解析层（写校验放行的凭证）；
 * ② 存量无 state 快照必须按 ACTIVE 兼容（零行为变化）；③ 渲染口径只放行 ACTIVE。
 */
describe('parseEnumValues 三态口径', () => {
  it('解析 DEPRECATED 标记（物化快照携带 state）', () => {
    const opts = parseEnumValues(
      JSON.stringify([
        { code: 'M', label: '男' },
        { code: 'U', label: '未知', state: 'DEPRECATED' },
      ]),
    );
    expect(opts).toHaveLength(2);
    expect(opts[0].state).toBeUndefined(); // ACTIVE 不写 state（字节级兼容存量）
    expect(opts[1].state).toBe('DEPRECATED');
  });

  it('DISABLED 标记同样透传（防御：异常快照不静默吞掉状态）', () => {
    const opts = parseEnumValues(JSON.stringify([{ code: 'X', label: '旧', state: 'DISABLED' }]));
    expect(opts[0].state).toBe('DISABLED');
  });

  it('存量快照（无 state）按 ACTIVE 兼容——行为逐字不变', () => {
    const opts = parseEnumValues(JSON.stringify([{ code: 'A', label: '甲' }]));
    expect(opts[0].state).toBeUndefined();
    // 渲染口径把「无 state」视作 ACTIVE
    expect(activeOnly(opts).map((o) => o.code)).toEqual(['A']);
  });

  it('非法/脏输入不抛异常，按空处理', () => {
    expect(parseEnumValues('not-json')).toEqual([]);
    expect(parseEnumValues(null)).toEqual([]);
    expect(parseEnumValues(JSON.stringify({ code: 'A' }))).toEqual([]);
  });

  it('渲染口径只放行 ACTIVE；写校验口径放行 ACTIVE + DEPRECATED', () => {
    const opts = parseEnumValues(
      JSON.stringify([
        { code: 'A', label: '甲' },
        { code: 'B', label: '乙', state: 'DEPRECATED' },
      ]),
    );
    // 渲染（新表单下拉）：只出现 ACTIVE
    expect(activeOnly(opts).map((o) => o.code)).toEqual(['A']);
    // 写校验（存量写放行）：两态都接受
    expect(writable(opts).map((o) => o.code)).toEqual(['A', 'B']);
  });
});

/** 渲染口径：新表单下拉只出现 ACTIVE（state 缺省按 ACTIVE 兼容存量）。 */
function activeOnly(opts: EnumOption[]): EnumOption[] {
  return opts.filter((o) => (o.state ?? 'ACTIVE') === 'ACTIVE');
}

/** 写校验口径：ACTIVE + DEPRECATED 放行，DISABLED 拒绝。 */
function writable(opts: EnumOption[]): EnumOption[] {
  return opts.filter((o) => o.state !== 'DISABLED');
}
