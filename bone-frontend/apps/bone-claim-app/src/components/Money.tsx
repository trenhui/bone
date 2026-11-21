import type { ReactElement } from 'react';
import { InputNumber } from 'antd';
import type { InputNumberProps } from 'antd';
export { formatMoney } from '../services/money';
import { formatMoney } from '../services/money';

/**
 * 金额展示/录入的单一口径。
 *
 * <p>此前各页各自 `¥${Number(v ?? 0).toFixed(2)}`：`null/NaN` 会被伪装成 `¥0.00`，
 * 在核定金额、可用额度、拒赔金额等位置等于把「未知」说成「零赔付/零额度」，足以误导人工终审。
 *
 * <p>统一规则：**只有显式数字（含 0）才格式化为金额；缺值/非法值显示 `—`（或调用方指定的文案）**。
 * 0 是合法金额，不能被当成缺值；缺值也绝不回落成 0。
 */
export interface MoneyTextProps {
  value: number | string | null | undefined;
  /** 业务语义更明确的缺值文案，如「待核定」「不限」；默认 `—`。 */
  unknownText?: string;
  className?: string;
  title?: string;
}

export function MoneyText({ value, unknownText, className, title }: MoneyTextProps): ReactElement {
  const text = formatMoney(value, { unknownText });
  const label = value == null || value === '' || !Number.isFinite(Number(value))
    ? (unknownText ?? '金额未知')
    : `金额 ${Number(value).toFixed(2)} 元`;
  return (
    <span className={className} title={title} aria-label={label}>
      {text}
    </span>
  );
}

export interface MoneyInputProps extends Omit<InputNumberProps<number>, 'precision' | 'addonAfter'> {
  /** 明确传入的缺值说明，避免输入框空值被理解为 0。 */
  emptyHint?: string;
}

/** 金额录入：保留各页的 min/max/校验规则，只统一精度与单位后缀。 */
export function MoneyInput({ emptyHint = '未填写', ...rest }: MoneyInputProps): ReactElement {
  return <InputNumber<number> {...rest} precision={2} addonAfter="元" placeholder={rest.placeholder ?? emptyHint} />;
}
