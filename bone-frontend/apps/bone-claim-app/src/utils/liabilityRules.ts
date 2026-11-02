/**
 * 责任配置（十一维规则）的成对联动校验纯函数。
 *
 * <p>与后端 {@code LiabilityConfigApplicationService#assertRuleUsable} 同一口径。
 *
 * <p><b>为什么前端也要判一次</b>：这三对字段（金额↔口径、模式↔名单、策略↔目录编码）只配一边时，
 * 库里是合法数据、页面上看着"已配置"，但理算期会整类拒绝——配置岗看不出任何异常。后端已经会拒，
 * 前端这一层只是把这次往返省掉，并让错误出现在<b>出问题的那个字段下面</b>而不是顶部一条 toast。
 * 判定权仍在服务端：这里漏判只会多点一次保存，不会让脏配置进库。
 *
 * <p>校验器只挂在"主字段"上（金额 / 模式 / 策略）并用 {@code dependencies} 联动另一侧：
 * 一个矛盾只报一次错，不会两个字段各弹一条。
 *
 * <p>本文件为纯函数、无框架依赖，便于单元测试（见 liabilityRules.test.ts）。
 */

export type FieldGetter = (name: string) => unknown;
export type PairCheck = (getFieldValue: FieldGetter) => string | null;

/** 数值型字段读成数字（antd InputNumber 在 stringMode 下给的是字符串）。 */
export function toNum(v: unknown): number {
  const n = Number(v ?? 0);
  return Number.isFinite(n) ? n : 0;
}

/** 日限额金额 ↔ 口径：两边必须同时配或同时不配。 */
export function dailyPairProblem(getFieldValue: FieldGetter): string | null {
  const hasLimit = toNum(getFieldValue('dailyLimit')) > 0;
  const basis = String(getFieldValue('dailyLimitBasis') ?? 'NONE');
  const hasBasis = basis !== '' && basis !== 'NONE';
  if (hasLimit && !hasBasis) {
    return '配了日限额就必须选「日」的口径，否则判不出「同日」是哪一天';
  }
  if (!hasLimit && hasBasis) {
    return '选了日窗口口径但日限额为 0，该口径永远不会生效（会被读成不限额）';
  }
  return null;
}

/** 名单字段值归一：库选择器（tags 多选）给数组，批量导入/旧数据给逗号串 —— 统一成逗号串判空。 */
export function codesText(v: unknown): string {
  if (Array.isArray(v)) {
    return v.map((c) => String(c).trim()).filter(Boolean).join(',');
  }
  return String(v ?? '').trim();
}

/** 逗号分隔编码串 → 库选择器的数组值（编辑回显用；空串给 undefined 让表单留空）。 */
export function toCodeList(v?: string | null): string[] {
  return (v ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

/** 适用限定：开了白/黑名单就必须给名单（空名单 = 全不赔 或 等于不设限，都不是业务意图）。 */
export function limitModePairProblem(getFieldValue: FieldGetter): string | null {
  const mode = String(getFieldValue('limitMode') ?? 'NONE');
  if (mode === '' || mode === 'NONE') {
    return null;
  }
  const hospitals = codesText(getFieldValue('limitHospitalCodes'));
  const diagnoses = codesText(getFieldValue('limitDiagnosisCodes'));
  if (!hospitals && !diagnoses) {
    return '启用了适用限定就必须选医院或病种名单，否则白名单等于全不赔、黑名单等于不设限';
  }
  return null;
}

/** 目录准入：策略与目录编码必须同配或同不配。 */
export function catalogPairProblem(getFieldValue: FieldGetter): string | null {
  const policy = String(getFieldValue('catalogPolicy') ?? 'NONE');
  const code = codesText(getFieldValue('catalogCode'));
  if ((policy === '' || policy === 'NONE') && code) {
    return '策略为「不查目录」时不该选药品目录——该编码永远不会被查询';
  }
  if (policy === 'MANDATORY' && !code) {
    return '策略为「必须查到结论」时必须选药品目录编码，否则不知道该查哪一版目录';
  }
  return null;
}

/**
 * 把成对校验函数包装成 antd Form 的 Rule。挂在"主字段"上，用 {@code dependencies} 联动另一侧，
 * 一个矛盾只报一次错。
 */
export function pairValidator(check: PairCheck) {
  return ({ getFieldValue }: { getFieldValue: FieldGetter }) => ({
    validator() {
      const problem = check(getFieldValue);
      return problem ? Promise.reject(new Error(problem)) : Promise.resolve();
    },
  });
}
