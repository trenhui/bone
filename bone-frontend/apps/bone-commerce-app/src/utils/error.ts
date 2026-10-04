/**
 * 统一错误文案提取。
 *
 * 后端统一信封下，HTTP 错误响应体仍是 `ApiResponse<ProblemDetail>`：
 * `{ code, message, data: { errorCode, detail, ... } }`，其中 `message` 是面向用户的
 * 中文摘要（如「订单不支持当前操作:当前状态不允许发货」），`data.errorCode` 是稳定机器码
 * （如 `BP_ORDER_STATUS_CONFLICT`）。
 *
 * `createApiClient` 的响应拦截器已把二者挂到 error 上（`displayMessage` / `errorCode`），
 * 这里优先取它们；取不到再回落到 axios 原生结构。
 */
export function errMsg(error: unknown, fallback: string): string {
  const e = error as {
    displayMessage?: string;
    errorCode?: string;
    response?: { data?: { message?: string; data?: { errorCode?: string } } };
    message?: string;
  };
  const text =
    e?.displayMessage ??
    e?.response?.data?.message ??
    e?.message ??
    fallback;
  const code = e?.errorCode ?? e?.response?.data?.data?.errorCode;
  return code ? `${text}（${code}）` : text;
}
