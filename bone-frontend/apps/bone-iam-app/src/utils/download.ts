/**
 * 文件下载统一封装（详设 §5.8「导出类接口契约」）。
 *
 * 导出类端点不走 `ApiResponse` 信封，直接返回二进制流 + `Content-Disposition: attachment`。
 * 这里负责：按 RFC 6266 还原文件名、触发浏览器下载、释放 ObjectURL。
 *
 * <p>已知边界：共享 `createApiClient` 的响应拦截器是 `(response) => response.data`，
 * **响应头不会透传到调用方**，因此 `Content-Disposition` / `X-Export-Truncated` 目前取不到，
 * 文件名走调用方传入的兜底名。彻底解决需要给 `createApiClient` 增加「原始响应」出口
 * （已在复核报告登记为建议项，本轮不扩大改动面）。
 */

/** 从 `Content-Disposition` 还原文件名（兼容 `filename*=UTF-8''`）。 */
export function filenameFromDisposition(
  disposition: string | undefined,
  fallback: string,
): string {
  if (!disposition) return fallback;
  const star = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(disposition);
  if (star?.[1]) {
    try {
      return decodeURIComponent(star[1]);
    } catch {
      /* 非法编码时退回普通分支 */
    }
  }
  const plain = /filename\s*=\s*"?([^";]+)"?/i.exec(disposition);
  return plain?.[1] ? plain[1] : fallback;
}

/** 触发浏览器下载并释放临时 URL。 */
export function downloadBlob(
  blob: Blob,
  fallbackName: string,
  disposition?: string | undefined,
): void {
  const url = URL.createObjectURL(blob);
  try {
    const a = document.createElement('a');
    a.href = url;
    a.download = filenameFromDisposition(disposition, fallbackName);
    document.body.appendChild(a);
    a.click();
    a.remove();
  } finally {
    URL.revokeObjectURL(url);
  }
}
