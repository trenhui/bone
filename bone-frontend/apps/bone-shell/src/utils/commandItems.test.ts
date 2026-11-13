// @vitest-environment node
import { describe, it, expect } from 'vitest';
import {
  MAX_COMMAND_RESULTS, buildCommandItems, flattenMenuLeaves, menuTarget,
  type MenuLeaf,
} from './commandItems';
import type { ShellMenuItem } from '../shellContext';

const config: ShellMenuItem[] = [
  {
    key: 'g1', label: '智能理赔', path: '', icon: null, children: [
      { key: 'm1', label: '核赔工作台', path: '/claim', hash: '/review', icon: null },
      { key: 'm2', label: '保单管理', path: '/claim', hash: '/policy', icon: null },
      { key: 'm3', label: '理赔进度', path: '/claim', hash: '/progress', icon: null },
    ],
  },
  { key: 'm4', label: '组织账号', path: '/iam', hash: '/accounts', icon: null },
];

describe('全局命令面板结果构造', () => {
  it('只收有 path 的叶子，目录节点不产生入口', () => {
    const leaves = flattenMenuLeaves(config);
    expect(leaves.map((l) => l.key)).toEqual(['m1', 'm2', 'm3', 'm4']);
    expect(leaves).toHaveLength(4);
  });

  it('target = pathname + hash，且总是站内绝对路径', () => {
    expect(menuTarget({ key: 'x', label: 'x', path: '/claim', hash: '/review' } as MenuLeaf)).toBe('/claim#/review');
    expect(menuTarget({ key: 'x', label: 'x', path: '/dashboard' } as MenuLeaf)).toBe('/dashboard');
    for (const item of buildCommandItems(config, '')) expect(item.target.startsWith('/')).toBe(true);
  });

  it('按菜单名检索，结果数有上限', () => {
    const items = buildCommandItems(config, '理赔');
    expect(items.some((i) => i.label === '理赔进度')).toBe(true);
    expect(items.length).toBeLessThanOrEqual(MAX_COMMAND_RESULTS);
  });

  it('纯数字输入优先给案件 ID 直达，且带 claimId 与 tab', () => {
    const items = buildCommandItems(config, '1001');
    expect(items[0].group).toBe('case');
    expect(items[0].target).toBe('/claim#/case?claimId=1001&tab=overview');
  });

  it('负向：IAM 未下发的菜单绝不能出现在结果里（防越权入口）', () => {
    const restricted = config.filter((n) => n.key === 'g1');
    const items = buildCommandItems(restricted, '组织账号');
    expect(items).toEqual([]);
    expect(buildCommandItems(restricted, '').some((i) => i.label === '组织账号')).toBe(false);
  });

  it('负向：非数字关键字不生成案件直达，且任何结果都不含危险 scheme', () => {
    const items = buildCommandItems(config, 'javascript:alert(1)');
    expect(items.some((i) => i.group === 'case')).toBe(false);
    // 关键：必须拿**有结果**的一批来验，空数组上的循环等于没测（防假绿）。
    const all = buildCommandItems(config, '');
    expect(all.length).toBeGreaterThan(0);
    for (const item of [...items, ...all]) expect(item.target).not.toMatch(/javascript:|data:/i);
  });

  it('无结果返回空数组，由 UI 给文字引导而不是硬编码业务链接', () => {
    expect(buildCommandItems(config, '完全不存在的功能')).toEqual([]);
    expect(buildCommandItems([], '')).toEqual([]);
  });

  it('超长输入被截断，绝不生成案件直达项；32 位合法 ID 仍可直达', () => {
    expect(buildCommandItems(config, 'x'.repeat(500))).toEqual([]);
    const long = buildCommandItems(config, '1'.repeat(500));
    expect(long.some((i) => i.group === 'case')).toBe(false);
    for (const item of long) expect(item.target.length).toBeLessThan(200);
    const valid = buildCommandItems(config, '1'.repeat(32));
    expect(valid[0]?.group).toBe('case');
    expect(valid[0]?.target.length).toBeLessThan(200);
  });
});
