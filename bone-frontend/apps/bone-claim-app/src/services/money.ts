/**
 * 金额格式化的唯一口径（纯函数，不依赖 React，可被 service 与组件共用）。
 *
 * <p>只有显式数字（含 0）才输出金额；`null/undefined/NaN` 输出缺值文案（默认 `—`）。
 * 绝不把「未知」伪装成 `¥0.00`——那在核定金额、可用额度、拒赔金额处会被读成"零赔付/零额度"。
 *
 * <p>同时固定千分位与两位小数：理赔金额常达六七位，`¥1234567.89` 在审核、对账、打印场景下
 * 极易看错一位。展示口径统一交给 `Intl.NumberFormat`，避免各页 `toFixed` 拼接漂移；
 * 该函数只处理金额，**不得**用于雪花 ID / 保单号等标识（那些必须原样字符串输出）。
 */
const AMOUNT_FORMATTER = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatMoney(
  value: number | string | null | undefined,
  options?: { unknownText?: string },
): string {
  if (value == null || value === '') return options?.unknownText ?? '—';
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return options?.unknownText ?? '—';
  return `¥${AMOUNT_FORMATTER.format(numeric)}`;
}
