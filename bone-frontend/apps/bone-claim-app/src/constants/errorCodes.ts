/**
 * 业务错误码常量（单一真源）。与后端 {@code SC_} 错误码对应。
 *
 * <p>配置面并发冲突分支原本散落硬编码的 {@code 'SC_CLAIM_CONCURRENT_CONFLICT'}，
 * 收口为常量后可与后端错误码保持单一来源。
 */
export const ERROR_CODES = {
  CONCURRENT_CONFLICT: 'SC_CLAIM_CONCURRENT_CONFLICT',
} as const;

export type ErrorCode = (typeof ERROR_CODES)[keyof typeof ERROR_CODES];
