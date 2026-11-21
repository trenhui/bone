/**
 * 上传文件名的前端快速失败闸门。
 *
 * <p>口径**严格对齐**平台后端 `FileObjectKeyGenerator.assertSafeName`：
 * 空名 / `..` 路径遍历 / 绝对路径 `/` / 反斜杠 `\` / NUL / ISO 控制字符。
 * 不额外收窄字符集（例如不禁止 `#`、`&`、`%`）：后端允许的字符若在前端被拒，
 * 属于误报，会把合规材料挡在门外——误报比漏报更致命。
 *
 * <p>这只是快速失败，服务端仍会复核；两端规则同口径，避免"前端能传、后端400"的割裂。
 */
export function isSafeFileName(name: string | null | undefined): boolean {
  if (!name || !name.trim()) return false;
  if (name.includes('..') || name.startsWith('/') || name.includes('\\') || name.includes('\0')) {
    return false;
  }
  for (const ch of name) {
    const code = ch.charCodeAt(0);
    // Character.isISOControl: U+0000..U+001F 与 U+007F..U+009F
    if (code <= 0x1f || (code >= 0x7f && code <= 0x9f)) return false;
  }
  return true;
}

/** 与 i18n `errors.FILE_NAME_INVALID` 同口径的用户提示。 */
export const FILE_NAME_INVALID_HINT =
  '文件名非法：不允许路径遍历（..）、绝对路径、反斜杠或控制字符，请重命名后重试';
