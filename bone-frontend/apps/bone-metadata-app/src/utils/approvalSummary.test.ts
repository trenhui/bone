import { describe, expect, it } from 'vitest';
import { approvalCellOf } from './approvalSummary';

/**
 * 审批摘要单元格判据单测。
 *
 * <p>重点锁三条**负向**口径（都是真实事故形态）：
 * ① 未进流程 ≠ 待指派（显示破折号，不能给用户「等某人审批」的错觉）；
 * ② 姓名解析不出来时**不得**回退成账户 id（9104 对业务用户是噪音，且是内部标识泄漏）；
 * ③ 多人待办必须体现「多人」（会签/加签是引擎已支持的真实能力，被压成一个人就是失真）。
 */
describe('approvalCellOf', () => {
  const names = { '9104': '张伟', '9105': '李娜' };

  it('未进入审批流 → 破折号 + 中性色（不误报待指派）', () => {
    const view = approvalCellOf({ started: false });
    expect(view.label).toBe('—');
    expect(view.tone).toBeNull();
  });

  it('undefined 摘要同样降级为破折号，不抛错', () => {
    expect(approvalCellOf(undefined).tone).toBeNull();
  });

  it('已通过 → 绿色，不显示办理人（历史不是「当前审批人」）', () => {
    const view = approvalCellOf(
      { started: true, phase: 'APPROVED', currentNode: 'end', currentNodeLabel: '结束' },
      names,
    );
    expect(view.label).toBe('已通过');
    expect(view.tone).toBe('success');
  });

  it('驳回 → 红色（与「退回返工」的橙色在详情里区分，此处只表达终态）', () => {
    expect(approvalCellOf({ started: true, phase: 'REJECTED' }, names).tone).toBe('error');
  });

  it('挂起 → 橙色且提示可执行的处置动作', () => {
    const view = approvalCellOf({ started: true, phase: 'SUSPENDED' }, names);
    expect(view.label).toBe('待指派');
    expect(view.tone).toBe('warning');
    expect(view.detail).toContain('指派');
  });

  it('流转中单人 → 直接显示姓名（列表扫读的就是「卡在谁那里」）', () => {
    const view = approvalCellOf(
      {
        started: true,
        phase: 'RUNNING',
        currentNode: 'dept',
        currentNodeLabel: '部门负责人审批',
        pendingAssignees: ['9104'],
      },
      names,
    );
    expect(view.label).toBe('张伟');
    expect(view.detail).toContain('部门负责人审批');
  });

  it('流转中多人（会签）→ 「首位 等 N 人」，不把多人压成一人', () => {
    const view = approvalCellOf(
      {
        started: true,
        phase: 'RUNNING',
        currentNode: 'finance_cosign',
        currentNodeLabel: '大额会签',
        pendingAssignees: ['9104', '9105'],
      },
      names,
    );
    expect(view.label).toBe('张伟 等 2 人');
    expect(view.detail).toContain('李娜');
  });

  it('无姓名目录 → 「N 人待办」，绝不回退成账户 id', () => {
    const view = approvalCellOf(
      { started: true, phase: 'RUNNING', pendingAssignees: ['9104', '9105'] },
      {},
    );
    expect(view.label).toBe('2 人待办');
    expect(view.label).not.toContain('9104');
  });

  it('RUNNING 但零待办 → 流转中（节点切换的瞬时态，不显示空白）', () => {
    const view = approvalCellOf({ started: true, phase: 'RUNNING', pendingAssignees: [] }, names);
    expect(view.label).toBe('流转中');
    expect(view.tone).toBe('processing');
  });
});
