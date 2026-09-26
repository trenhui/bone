/**
 * 日期 / 数字格式化（i18n 方案 §5.6）。
 *
 * 时间口径：后端按 §6.4 输出**带偏移**的 ISO-8601（`...Z`）；不带偏移的输入说明后端尚未改造，
 * 这里**不静默兜底**（不假设它是 UTC），而是开发环境下告警 + 原样解析——宁可显示后端原始值，
 * 也不要凭空 +8h 把后端缺陷伪装成前端正常。
 */
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import timezone from 'dayjs/plugin/timezone';
import utc from 'dayjs/plugin/utc';

import 'dayjs/locale/en';
import 'dayjs/locale/zh-cn';

import { currentLocale } from './index';

// 注意：dayjs 的 `extend()` 类型声明返回 `Dayjs`（链式调用会报错），必须逐个调用
dayjs.extend(utc);
dayjs.extend(timezone);
dayjs.extend(relativeTime);

/** i18next locale → dayjs locale（加语言时只扩展这张表） */
const I18N_TO_DAYJS_LOCALE: Record<string, string> = {
  'zh-CN': 'zh-cn',
  'en-US': 'en',
};

const getDayjsLocale = (lng: string): string => I18N_TO_DAYJS_LOCALE[lng] ?? 'zh-cn';

/** 浏览器本地时区，如 "Asia/Shanghai" */
const LOCAL_ZONE = Intl.DateTimeFormat().resolvedOptions().timeZone ?? 'Asia/Shanghai';

/** 带偏移（Z 或 ±HH:mm）才认为是绝对时刻 */
const HAS_OFFSET = /(?:Z|[+-]\d{2}:?\d{2})$/;

function parse(v: string | Date) {
  const raw = typeof v === 'string' ? v : v.toISOString();
  if (typeof v === 'string' && !HAS_OFFSET.test(v) && import.meta.env?.DEV) {
    console.warn('[i18n] 时间字段无时区偏移，后端未按 §6.4 输出 UTC：', v);
  }
  return HAS_OFFSET.test(raw) ? dayjs(raw).tz(LOCAL_ZONE) : dayjs(raw);
}

function withLocale(v: string | Date) {
  return parse(v).locale(getDayjsLocale(currentLocale()));
}

/** 日期时间：YYYY-MM-DD HH:mm:ss */
export function formatDate(v: string | Date | null | undefined): string {
  if (!v) return '-';
  return withLocale(v).format('YYYY-MM-DD HH:mm:ss');
}

/** 只到天：YYYY-MM-DD */
export function formatDateOnly(v: string | Date | null | undefined): string {
  if (!v) return '-';
  return withLocale(v).format('YYYY-MM-DD');
}

/** 相对时间："3 分钟前" / "3 minutes ago" */
export function formatRelative(v: string | Date | null | undefined): string {
  if (!v) return '-';
  return withLocale(v).fromNow();
}

/** 数字 / 百分比（Intl 原生，按当前 locale） */
export function formatNumber(
  v: number | string,
  options?: Intl.NumberFormatOptions
): string {
  const n = typeof v === 'string' ? Number(v) : v;
  if (Number.isNaN(n)) return '-';
  return new Intl.NumberFormat(currentLocale(), options).format(n);
}
