import { describe, expect, it } from 'vitest';
import {
  enforceableFieldCodes,
  runSubmit,
  validateControlValues,
  type Control,
  type LinkageRule,
} from '@bone/ui-schema';

/**
 * 必填强制域收窄回归（前后端口径对齐）。
 *
 * <p><b>缺陷形态</b>：联动规则写成「when X isNull ⇒ then X required」的自引用死锁时，字段 X 恒空、
 * 必填恒不满足 ⇒ 所有提交被拦。实证对象是理赔样例的 {@code LINK-EVID-01}：
 * {@code when evidences isNull ⇒ then evidences required}，而 evidences 是 ATTACHMENT 控件，
 * 值归关系端点管、主记录无物理列，前端却比后端严地把所有申请都拦在提交外。
 *
 * <p><b>判据为「性质」</b>：①复现死锁（不收窄必红）②收窄后放行 ③物理列字段的必填<b>不被</b>放宽。
 * 删掉实现里的 {@code enforceableFields} 收窄，②会转红——不是空转绿灯。
 */
describe('必填强制域收窄：无物理列字段不参与必填', () => {
  // 自引用死锁规则：与理赔样例 LINK-EVID-01 同构
  const deadlockLinkage: LinkageRule[] = [
    {
      name: '材料缺失转补件提示',
      when: { fieldCode: 'evidences', operator: 'isNull' },
      then: [{ fieldCode: 'evidences', required: true, notice: '材料缺失请补件，不阻塞其他案件' }],
      code: 'LINK-EVID-01',
    },
    // 对照组：物理列字段的必填必须仍然生效
    {
      name: '专票必填税号',
      when: { fieldCode: 'invoice_type', operator: 'eq', value: 'SPECIAL' },
      then: [{ fieldCode: 'tax_no', required: true }],
      code: 'LINK-TAX-01',
    },
  ];

  const attachmentControl: Control = {
    fieldCode: 'evidences',
    controlKind: 'ATTACHMENT',
    label: '材料证据',
  } as Control;

  // 控件自身显式声明 required（控件级必填覆盖路径的前置条件）
  const requiredAttachmentControl: Control = {
    fieldCode: 'evidences',
    controlKind: 'ATTACHMENT',
    label: '材料证据',
    required: true,
  } as Control;

  const textControl: Control = {
    fieldCode: 'tax_no',
    controlKind: 'INPUT',
    label: '税号',
    required: true,
  } as Control;

  const doc = (over: Record<string, unknown> = {}) => ({
    policy_no: 'P20261025001',
    evidences: null,
    invoice_type: 'NORMAL',
    tax_no: '',
    ...over,
  });

  const fieldCodes = ['policy_no', 'evidences', 'invoice_type', 'tax_no'];
  const enforceable = enforceableFieldCodes([attachmentControl, textControl], fieldCodes);

  it('①复现：不收窄时自引用必填恒拦提交（前端比后端严的旧形态）', () => {
    const r = runSubmit(doc(), [], deadlockLinkage);
    expect(r.errors.some((e) => e.includes('evidences') && e.includes('不能为空'))).toBe(true);
  });

  it('②收窄后：附件字段的必填强制被跳过，提交放行', () => {
    const r = runSubmit(doc(), [], deadlockLinkage, {}, {}, enforceable);
    expect(r.errors.some((e) => e.includes('evidences'))).toBe(false);
  });

  it('③不过度放宽：物理列字段的必填仍生效', () => {
    const r = runSubmit(
      doc({ invoice_type: 'SPECIAL', tax_no: '' }),
      [],
      deadlockLinkage,
      {},
      {},
      enforceable,
    );
    expect(r.errors.some((e) => e.includes('tax_no') && e.includes('不能为空'))).toBe(true);
  });

  it('④控件级同口径：附件控件显式 required 也不得阻断', () => {
    // validateControlValues 的必填覆盖路径与 runSubmit 产出同一句文案，必须同步收窄
    const withoutNarrow = validateControlValues(doc(), [requiredAttachmentControl], {});
    expect(withoutNarrow.errors.some((e) => e.includes('evidences'))).toBe(true);

    const withNarrow = validateControlValues(doc(), [requiredAttachmentControl], {}, enforceable);
    expect(withNarrow.errors.some((e) => e.includes('evidences'))).toBe(false);
  });

  it('⑤收窄只跳必填，不吞掉控件其他约束（长度/正则照旧）', () => {
    const longTax = 'X'.repeat(30);
    const r = validateControlValues(
      doc({ tax_no: longTax }),
      [attachmentControl, { ...textControl, props: { maxLength: 10 } } as Control],
      {},
      enforceable,
    );
    expect(r.errors.some((e) => e.includes('tax_no') && e.includes('长度'))).toBe(true);
  });

  it('⑥enforceableFieldCodes：只排除无列种类，物理列字段全保留', () => {
    expect(enforceable.has('evidences')).toBe(false);
    expect(enforceable.has('policy_no')).toBe(true);
    expect(enforceable.has('invoice_type')).toBe(true);
    expect(enforceable.has('tax_no')).toBe(true);
  });

  it('⑦CHILD_TABLE 同属无列种类，一并收窄', () => {
    const child: Control = { fieldCode: 'items', controlKind: 'CHILD_TABLE', label: '明细' } as Control;
    const set = enforceableFieldCodes([attachmentControl, child, textControl], fieldCodes.concat('items'));
    expect(set.has('items')).toBe(false);
    expect(set.has('evidences')).toBe(false);
    expect(set.has('tax_no')).toBe(true);
  });
});
