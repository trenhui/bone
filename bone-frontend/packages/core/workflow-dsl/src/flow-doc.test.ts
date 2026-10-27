import { describe, expect, it } from 'vitest';

import { compile, FlowDocCompileError, START_CODE, END_CODE } from './compile';
import { parse } from './parse';
import { groupViolationsByNode, hintOf, isPublishable } from './validation';
import type { FlowDoc, WorkflowDefinitionJson } from './types';

/** 底座文档：发起 → 直属主管 → 结束（最小可发布形态）。 */
function linearDoc(): FlowDoc {
  return {
    key: 'expense',
    name: '费用报销',
    steps: [
      {
        kind: 'approve',
        code: 'mgr',
        label: '直属主管',
        mode: 'SINGLE',
        rules: [{ strategy: 'LINE_MANAGER', params: {} }],
      },
    ],
  };
}

/** 带条件分支的文档：主管 →（金额判断）→ 小额：财务复核 / 默认：CFO → 结束。 */
function branchingDoc(): FlowDoc {
  return {
    key: 'expense',
    name: '费用报销',
    steps: [
      {
        kind: 'approve',
        code: 'mgr',
        label: '直属主管',
        mode: 'SINGLE',
        rules: [{ strategy: 'LINE_MANAGER', params: {} }],
      },
      {
        kind: 'condition',
        code: 'amt',
        label: '金额判断',
        branches: [
          {
            id: 'b1',
            label: '小额',
            when: { fieldCode: 'amount', operator: 'lte', value: 5000 },
            body: [
              {
                kind: 'approve',
                code: 'fin_check',
                label: '财务复核',
                mode: 'SINGLE',
                rules: [{ strategy: 'ROLE', params: { roleCode: 'fin' } }],
              },
            ],
          },
        ],
        defaultBranch: {
          id: 'bd',
          label: '默认',
          when: null,
          body: [
            {
              kind: 'approve',
              code: 'cfo',
              label: 'CFO',
              mode: 'RATIO',
              quorum: 2,
              rules: [{ strategy: 'ROLE', params: { roleCode: 'cfo' } }],
            },
          ],
        },
      },
    ],
  };
}

describe('compile：文档树 → 引擎定义', () => {
  it('线性流程：自动补 START/END，审批节点恰一条出边', () => {
    const definition = compile(linearDoc());

    expect(definition.nodes.map((node) => node.code)).toEqual([START_CODE, 'mgr', END_CODE]);
    expect(definition.nodes.find((node) => node.code === 'mgr')).toMatchObject({
      type: 'APPROVE',
      mode: 'SINGLE',
    });
    expect(definition.edges).toEqual([
      { from: START_CODE, to: 'mgr', when: null, label: null },
      { from: 'mgr', to: END_CODE, when: null, label: null },
    ]);
    expect(definition.assignments.mgr).toEqual([{ strategy: 'LINE_MANAGER', params: {} }]);
    expect(definition.engineSchemaVersion).toBe(1);
  });

  it('空主链也产出合法形态：START 直连 END（不制造断头）', () => {
    const definition = compile({ key: 'empty', name: '空流程', steps: [] });
    expect(definition.edges).toEqual([{ from: START_CODE, to: END_CODE, when: null, label: null }]);
  });

  it('条件节点：显式分支按序、默认边固定末位（WF-006 在结构层即成立）', () => {
    const definition = compile(branchingDoc());
    const outs = definition.edges.filter((edge) => edge.from === 'amt');

    expect(outs).toHaveLength(2);
    expect(outs[0].to).toBe('fin_check');
    expect(outs[0].when).toEqual({ fieldCode: 'amount', operator: 'lte', value: 5000 });
    expect(outs[1]).toMatchObject({ to: 'cfo', when: null });
    expect(outs[outs.length - 1].when).toBeNull();
  });

  it('分支汇合：两条分支都连回同一后继（不是各复制一份后续链）', () => {
    const definition = compile(branchingDoc());
    expect(definition.edges.filter((edge) => edge.from === 'fin_check')).toEqual([
      { from: 'fin_check', to: END_CODE, when: null, label: null },
    ]);
    expect(definition.edges.filter((edge) => edge.from === 'cfo')).toEqual([
      { from: 'cfo', to: END_CODE, when: null, label: null },
    ]);
  });

  it('空分支体：条件节点直接连到后续节点，不产生悬空边', () => {
    const doc = branchingDoc();
    const condition = doc.steps[1];
    if (condition.kind !== 'condition') throw new Error('夹具错误');
    condition.branches[0].body = [];

    const definition = compile(doc);
    const outs = definition.edges.filter((edge) => edge.from === 'amt');
    expect(outs[0].to).toBe(END_CODE);
    expect(definition.nodes.map((node) => node.code)).not.toContain('fin_check');
  });

  it('quorum 只在 RATIO 下写出（非 RATIO 的 quorum 引擎不读，不进库）', () => {
    const ratio = compile(branchingDoc());
    expect(ratio.nodes.find((node) => node.code === 'cfo')?.quorum).toBe(2);

    const doc = linearDoc();
    const step = doc.steps[0];
    if (step.kind !== 'approve') throw new Error('夹具错误');
    step.mode = 'ALL';
    step.quorum = 3;
    expect(compile(doc).nodes.find((node) => node.code === 'mgr')?.quorum).toBeUndefined();
  });

  it('节点顺序按遍历序稳定输出（同一文档编译两次结果完全一致）', () => {
    const first = compile(branchingDoc());
    const second = compile(branchingDoc());
    expect(first).toEqual(second);
    expect(first.nodes.map((node) => node.code)).toEqual([
      START_CODE,
      'mgr',
      'amt',
      'fin_check',
      'cfo',
      END_CODE,
    ]);
  });

  it('保留字与重复编码在编译期即拒绝（不留给后端校验）', () => {
    const reserved: FlowDoc = {
      key: 'k',
      name: 'k',
      steps: [
        {
          kind: 'approve',
          code: 'START',
          label: 'x',
          mode: 'SINGLE',
          rules: [],
        },
      ],
    };
    expect(() => compile(reserved)).toThrow(FlowDocCompileError);

    const duplicated: FlowDoc = {
      key: 'k',
      name: 'k',
      steps: [
        { kind: 'approve', code: 'a', label: 'a', mode: 'SINGLE', rules: [] },
        { kind: 'cc', code: 'a', label: 'a', rules: [] },
      ],
    };
    expect(() => compile(duplicated)).toThrow(/节点编码重复/);
  });

  it('ui 段承载布局与文档树原文，且不影响引擎可读字段', () => {
    const definition = compile(branchingDoc(), { layout: { mgr: { x: 0, y: 120 } } });
    expect(definition.ui?.layout).toEqual({ mgr: { x: 0, y: 120 } });
    expect(definition.ui?.flowDoc?.key).toBe('expense');
    expect(definition.key).toBe('expense');
  });
});

describe('parse：引擎定义 → 文档树', () => {
  it('带 ui.flowDoc 的定义：无损取回原文', () => {
    const doc = branchingDoc();
    const result = parse(compile(doc));

    expect(result.lossy).toBe(false);
    expect(result.doc).toEqual(doc);
  });

  it('旧定义（无 ui.flowDoc）：按图重建，结构与原文一致', () => {
    const doc = branchingDoc();
    const legacy: WorkflowDefinitionJson = compile(doc, { keepFlowDoc: false });
    expect(legacy.ui?.flowDoc).toBeUndefined();

    const result = parse(legacy);
    expect(result.lossy).toBe(true);
    expect(result.warnings.join()).toContain('旧数据重建');
    // 关键断言：重建出的文档树再编译，必须与旧定义逐字段一致（等价于「图 → 树 → 图」闭环）
    expect(compile(result.doc, { keepFlowDoc: false })).toEqual(legacy);
  });

  it('旧定义 + 嵌套条件：重建仍能还原分支体内的子条件', () => {
    const doc: FlowDoc = {
      key: 'nested',
      name: '嵌套',
      steps: [
        {
          kind: 'condition',
          code: 'outer',
          label: '外层',
          branches: [
            {
              id: 'o1',
              label: '是',
              when: { fieldCode: 'big', operator: 'eq', value: true },
              body: [
                {
                  kind: 'condition',
                  code: 'inner',
                  label: '内层',
                  branches: [
                    {
                      id: 'i1',
                      label: '是',
                      when: { fieldCode: 'vip', operator: 'eq', value: true },
                      body: [
                        {
                          kind: 'cc',
                          code: 'cc_vip',
                          label: '知会风控',
                          rules: [{ strategy: 'ROLE', params: { roleCode: 'risk' } }],
                        },
                      ],
                    },
                  ],
                  defaultBranch: { id: 'id', label: '默认', when: null, body: [] },
                },
              ],
            },
          ],
          defaultBranch: { id: 'od', label: '默认', when: null, body: [] },
        },
        {
          kind: 'approve',
          code: 'tail',
          label: '末尾审批',
          mode: 'ANY',
          rules: [{ strategy: 'DEPT_HEAD', params: {} }],
        },
      ],
    };

    const legacy = compile(doc, { keepFlowDoc: false });
    const result = parse(legacy);
    expect(compile(result.doc, { keepFlowDoc: false })).toEqual(legacy);
  });
});

describe('validation：只投影、不重写规则', () => {
  it('按 locator 分组，整图级违规归到 null', () => {
    const grouped = groupViolationsByNode([
      { code: 'WF-020', message: '缺 roleCode', locator: 'mgr' },
      { code: 'WF-003', message: '缺结束节点', locator: null },
      { code: 'WF-012', message: '缺完成方式', locator: 'mgr' },
    ]);

    expect(grouped.get('mgr')).toHaveLength(2);
    expect(grouped.get(null)).toHaveLength(1);
    expect(isPublishable([{ code: 'WF-003', message: 'x' }])).toBe(false);
    expect(isPublishable([])).toBe(true);
  });

  it('高频错误码给出可执行建议，未知码返回 null（界面直接展示后端 message）', () => {
    expect(hintOf({ code: 'WF-020', message: 'x' }).suggestion).toContain('挂起');
    expect(hintOf({ code: 'WF-999', message: 'x' }).suggestion).toBeNull();
  });
});
