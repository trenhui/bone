/**
 * 把后端返回的 Blob 触发为浏览器下载。
 *
 * <p><b>为什么不直接 `a.download = name`</b>：文件名取自 `Content-Disposition` 时需要解析
 * `filename="xxx.csv"`（后端已带当日日期后缀）；而 `responseType: 'blob'` 的响应经
 * axios 拦截器只留 body，headers 拿不到——所以由调用方传入文件名，这里负责
 * 建 object URL、挂 a 标签、点击、 revoke 的完整生命周期（不 revoke 会占住内存直到页面卸载）。
 */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  // revoke 放在 click 之后的下一个宏任务：同步 revoke 会让部分浏览器（尤其 Safari）
  // 来不及读取 blob 内容，下载得到 0 字节文件
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

/** 生成带当日日期的导出文件名（与后端 `CsvExport` 的命名约定一致）。 */
export function csvFilename(baseName: string): string {
  const today = new Date();
  const yyyy = today.getFullYear();
  const mm = String(today.getMonth() + 1).padStart(2, '0');
  const dd = String(today.getDate()).padStart(2, '0');
  return `${baseName}-${yyyy}-${mm}-${dd}.csv`;
}
