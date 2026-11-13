import { describe, it, expect } from 'vitest';
import {
  CLAIM_MENU_GROUP_BY_HASH, CLAIM_NAV_GROUP_ORDER, CLAIM_NAV_GROUP_TITLES,
  UNGROUPED_TITLE, claimHashOf, groupClaimMenuItems, groupForClaimHash, isClaimMenuLeaf,
} from './claimMenuGroups';

/** 与 bone-claim-app/src/routes.tsx 的非 hidden 路由声明顺序逐字一致（唯一真源的人工对账副本）。 */
const VISIBLE_HASHES = [
  '/pre-exam', '/review', '/adjust', '/evidence', '/fund', '/appeal', '/suspend',
  '/inspection', '/submit', '/progress', '/policy', '/quota', '/library',
  '/insurers', '/mappings',
];

const asLeaf = (hash: string, path = '/claim') => ({ hash, path, key: `k-${hash}` });

describe('智能理赔侧栏四空间常驻分组（纯展示层）', () => {
  it('四个标题恰好为 作业 / 查询 / 配置 / 治理，未知值另有「未分组」', () => {
    expect([...CLAIM_NAV_GROUP_TITLES]).toEqual(['作业', '查询', '配置', '治理']);
    expect([...CLAIM_NAV_GROUP_ORDER]).toEqual(['作业', '查询', '配置', '治理', '未分组']);
    expect(UNGROUPED_TITLE).toBe('未分组');
  });

  it('映射表精确覆盖 15 条可见菜单，且四组分别 8 / 2 / 3 / 2 条', () => {
    expect(Object.keys(CLAIM_MENU_GROUP_BY_HASH)).toHaveLength(15);
    for (const hash of VISIBLE_HASHES) {
      expect(groupForClaimHash(hash)).toBe(CLAIM_MENU_GROUP_BY_HASH[hash]);
    }
    const counts = CLAIM_NAV_GROUP_TITLES.map(
      (title) => Object.values(CLAIM_MENU_GROUP_BY_HASH).filter((g) => g === title).length,
    );
    expect(counts).toEqual([8, 2, 3, 2]);
  });

  it('四组是声明顺序上的连续区段：展平后逐项相等，菜单顺序一字不改', () => {
    const leaves = VISIBLE_HASHES.map((h) => asLeaf(h));
    const groups = groupClaimMenuItems(leaves);
    expect(groups.map((g) => g.title)).toEqual(['作业', '查询', '配置', '治理']);
    expect(groups.map((g) => g.items.length)).toEqual([8, 2, 3, 2]);
    expect(groups.flatMap((g) => g.items)).toEqual(leaves);
    expect(groups.flatMap((g) => g.items.map((i) => i.hash))).toEqual(VISIBLE_HASHES);
  });

  it('未登记 hash 显式落「未分组」且仍保留在结果中（不丢菜单、不改可达性）', () => {
    expect(groupForClaimHash('/brand-new')).toBe('未分组');
    expect(groupForClaimHash(null)).toBe('未分组');
    expect(groupForClaimHash(undefined)).toBe('未分组');
    const leaves = [...VISIBLE_HASHES.slice(0, 3).map((h) => asLeaf(h)), asLeaf('/brand-new')];
    const groups = groupClaimMenuItems(leaves);
    expect(groups.map((g) => g.title)).toEqual(['作业', '未分组']);
    expect(groups.flatMap((g) => g.items)).toEqual(leaves);
    expect(groups[1].items[0].key).toBe('k-/brand-new');
  });

  it('兼容只有 path 形如 /claim#/fund 的历史节点；其它域不被误判为理赔叶子', () => {
    expect(claimHashOf({ path: '/claim#/fund' })).toBe('/fund');
    expect(claimHashOf({ path: '/claim', hash: 'fund' })).toBe('/fund');
    expect(isClaimMenuLeaf({ path: '/claim', hash: '/fund' })).toBe(true);
    expect(isClaimMenuLeaf({ path: '/iam', hash: '/users' })).toBe(false);
    expect(isClaimMenuLeaf({ path: '/claim' })).toBe(false);
  });

  // 变异护栏：把映射改成跨段搬动时，展平顺序必须不再等于真源顺序（证明该判据有判别力）。
  it('变异：映射表若把 /library 改判到「查询」，连续性断言立刻失效', () => {
    const mutated: Record<string, string> = { ...CLAIM_MENU_GROUP_BY_HASH, '/library': '查询' };
    // 按组收集后的顺序与声明顺序不再一致 ⇒ 真实实现的「展平相等」断言会判红
    const grouped = CLAIM_NAV_GROUP_ORDER.flatMap(
      (title) => VISIBLE_HASHES.filter((h) => mutated[h] === title),
    );
    expect(grouped).not.toEqual(VISIBLE_HASHES);
    expect(grouped.indexOf('/library')).toBe(10);
    expect(VISIBLE_HASHES.indexOf('/library')).toBe(12);
  });
});
