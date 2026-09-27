/**
 * 后端错误 → 用户可见文案（国际化设计方案 §3 回退链）。
 *
 * <p><b>回退链</b>：{@code errors.<errorCode>} 译文 → 调用方传入的中文 fallback。
 *
 * <p><b>为什么不能直接展示后端 message</b>：后端 {@code message} 恒为中文——契约里它是 fallback
 * 而非展示文案（英文用户会看到中文），且形如 {@code SYS_CONFIG_NOT_FOUND: 42} 带码前缀，不适合直接给用户看。
 *
 * <p><b>为什么用 i18n 单例、而不是把 {@code t} 传进来</b>：错误提示是命令式 toast，不参与 React 重渲染；
 * 若要求传 {@code t}，每个 catch 都得先取 hooks，会把这些纯函数绑死在组件里。
 */
import i18n from 'i18next';

/**
 * 从任意错误对象里取稳定业务码。
 *
 * <p>两条来源：{@code error.errorCode}（{@code createApiClient} 响应拦截器挂上的）与
 * {@code error.response.data.data.errorCode}（未走拦截器时的原始 {@code ApiResponse<ProblemDetail>}）。
 */
function extractErrorCode(error: unknown): string | undefined {
  const err = error as {
    errorCode?: unknown;
    response?: { data?: { data?: { errorCode?: unknown } } };
  } | null;
  const direct = err?.errorCode;
  if (typeof direct === 'string' && direct.length > 0) {
    return direct;
  }
  const nested = err?.response?.data?.data?.errorCode;
  return typeof nested === 'string' && nested.length > 0 ? nested : undefined;
}

/**
 * 解析错误提示文案。
 *
 * @param error 任意捕获到的错误（通常是 axios 错误）
 * @param fallback 无码或译文缺失时的兜底文案（中文，与后端 message 同一契约层）
 */
export function resolveErrorMessage(error: unknown, fallback: string): string {
  const errorCode = extractErrorCode(error);
  if (!errorCode) {
    return fallback;
  }
  // defaultValue 给空串：i18next 在键缺失时会回传 key 本身（'errors.XXX'），不能直接展示
  const translated = i18n.t(`errors.${errorCode}`, { defaultValue: '' });
  return typeof translated === 'string' && translated.length > 0 ? translated : fallback;
}

/**
 * 只取业务码，供需要按码分支的场景（如 409 冲突跳表单校验、404 关闭抽屉）使用。
 */
export function resolveErrorCode(error: unknown): string | undefined {
  return extractErrorCode(error);
}
