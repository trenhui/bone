import { describe, expect, it } from 'vitest';
import type { UiCondition } from '@bone/ui-schema';
import {
  MAX_GROUP_DEPTH, addChild, childrenOf, depthOf, emptyGroup, emptyLeaf, groupKindOf, isGroup,
  removeChildAt, setGroupKind, unwrapGroup, updateChildAt, wrapAsGroup,
} from './conditionOps';

/**
 * 嵌套条件树纯函数单测。
 *
 * 负向探针纪律（防假绿）：
 * - 删空组必须退化为叶（空组 = 恒不命中的死配置，不能留在 schema 里）；
 * - not 组删空/改子必须走单子语义（not 只有单子，多子是协议外形态）；
 * - 深度上限必须与引擎 MAX_COND_DEPTH 对齐（超限分支引擎 fail-closed，
 *   编辑器放行 = 配出永远不生效的规则）；
 * - 不可变纪律：所有操作返回新对象，原组不被 mutate。
 */

const leaf = (code: string, value: unknown = '1'): UiCondition => ({ fieldCode: code, operator: 'eq', value });

describe('conditionOps：判定与构造', () => {
  it('isGroup：all/any/not 为组；简单条件与空对象为叶', () => {
    expect(isGroup({ all: [] })).toBe(true);
    expect(isGroup({ any: [] })).toBe(true);
    expect(isGroup({ not: leaf('a') })).toBe(true);
    expect(isGroup(leaf('a'))).toBe(false);
    expect(isGroup(undefined)).toBe(false);
    expect(isGroup({})).toBe(false);
  });

  it('groupKindOf/childrenOf：not 归一为单元素数组', () => {
    expect(groupKindOf({ all: [leaf('a')] })).toBe('all');
    expect(groupKindOf({ any: [leaf('a')] })).toBe('any');
    expect(groupKindOf({ not: leaf('a') })).toBe('not');
    expect(childrenOf({ not: leaf('a') })).toEqual([leaf('a')]);
  });

  it('emptyGroup：默认 all 且带一个待配叶（不是空数组）', () => {
    const g = emptyGroup();
    expect(g.all).toHaveLength(1);
    expect(g.all?.[0]).toEqual(emptyLeaf());
    expect(emptyGroup('any').any).toHaveLength(1);
  });

  it('depthOf：组嵌套深度（叶=0，root 组=1，逐层 +1）', () => {
    expect(depthOf(leaf('a'))).toBe(0);
    expect(depthOf({ all: [leaf('a')] })).toBe(1);
    expect(depthOf({ all: [{ any: [leaf('a')] }] })).toBe(2);
    expect(depthOf({ all: [{ any: [{ all: [leaf('a')] }] }] })).toBe(3);
    // 超过上限的嵌套必须能被识别（GroupTree 靠它禁配；引擎对第 4 层 fail-closed）
    expect(depthOf({ all: [{ any: [{ all: [{ any: [leaf('a')] }] }] }] })).toBe(4);
    expect(MAX_GROUP_DEPTH).toBe(3); // 引擎 MAX_COND_DEPTH 同值，改这里必须同步引擎
  });
});

describe('conditionOps：增删改', () => {
  it('addChild：普通组追加；not 组替换单子', () => {
    const g = { all: [leaf('a')] };
    expect(addChild(g, leaf('b')).all).toEqual([leaf('a'), leaf('b')]);
    const n = { not: leaf('a') };
    expect(addChild(n, leaf('b'))).toEqual({ not: leaf('b') });
  });

  it('addChild 子组：嵌套深度随之增加', () => {
    const g = addChild(emptyGroup(), emptyGroup());
    expect(depthOf(g)).toBe(2);
    expect(isGroup(g.all?.[1])).toBe(true);
  });

  it('removeChildAt：删中间项保序；删空退化为待配叶（负向：不留空组）', () => {
    const g = { all: [leaf('a'), leaf('b'), leaf('c')] };
    expect(removeChildAt(g, 1).all).toEqual([leaf('a'), leaf('c')]);
    const one = { all: [leaf('a')] };
    expect(removeChildAt(one, 0)).toEqual(emptyLeaf());
    expect(removeChildAt({ not: leaf('a') }, 0)).toEqual(emptyLeaf());
  });

  it('updateChildAt：只换目标下标，其余原样', () => {
    const g = { all: [leaf('a'), leaf('b')] };
    const next = updateChildAt(g, 0, { fieldCode: 'a', operator: 'gt', value: '9' });
    expect(next.all?.[0]).toEqual({ fieldCode: 'a', operator: 'gt', value: '9' });
    expect(next.all?.[1]).toEqual(leaf('b'));
  });

  it('setGroupKind：all↔any 无损保留子条件；切 not 保留首子', () => {
    const g = { all: [leaf('a'), leaf('b')] };
    expect(setGroupKind(g, 'any')).toEqual({ any: [leaf('a'), leaf('b')] });
    expect(setGroupKind(g, 'not')).toEqual({ not: leaf('a') });
    // 空子组（构造上不会出现，防御）切类型时补待配叶
    expect(setGroupKind({ all: [] }, 'any')).toEqual({ any: [emptyLeaf()] });
  });

  it('负向探针：not 组若被写成多子数组，childrenOf 口径必须仍可读（不抛）', () => {
    const weird = { not: leaf('a') } as UiCondition;
    expect(childrenOf(weird)).toHaveLength(1);
  });
});

describe('conditionOps：结构升降级（可视化 ↔ 简单 无损互切）', () => {
  it('wrapAsGroup：保留原条件为唯一子条件', () => {
    const wrapped = wrapAsGroup(leaf('amount', '100'));
    expect(wrapped).toEqual({ all: [{ fieldCode: 'amount', operator: 'eq', value: '100' }] });
  });

  it('unwrapGroup：单子非 not 组可拆回；多子或 not 返回 null（禁丢数据）', () => {
    expect(unwrapGroup({ all: [leaf('a')] })).toEqual(leaf('a'));
    expect(unwrapGroup({ any: [leaf('a')] })).toEqual(leaf('a'));
    expect(unwrapGroup({ all: [leaf('a'), leaf('b')] })).toBeNull();
    expect(unwrapGroup({ not: leaf('a') })).toBeNull();
  });

  it('round-trip：简单 → 组 → 简单，条件逐位相等', () => {
    const original: UiCondition = { fieldCode: 'amount', operator: 'gt', value: '500' };
    expect(unwrapGroup(wrapAsGroup(original))).toEqual(original);
  });

  it('负向探针：unwrapGroup 若对多子组也返回首子，本测试必须变红（丢条件）', () => {
    const multi = { all: [leaf('a'), leaf('b')] };
    expect(unwrapGroup(multi)).toBeNull();
  });
});

describe('conditionOps：不可变纪律', () => {
  it('全部写操作返回新对象，原组不被 mutate', () => {
    const g = { all: [leaf('a')] };
    const snap = JSON.stringify(g);
    addChild(g, leaf('b'));
    removeChildAt(g, 0);
    updateChildAt(g, 0, leaf('z'));
    setGroupKind(g, 'any');
    expect(JSON.stringify(g)).toBe(snap);
  });
});
