package com.bone.file.common;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 对象键生成器与校验器：把「租户归属」「路径安全」「扩展名白名单」收口到一处。
 *
 * <p><b>为何键要带租户前缀</b>：在 {@code file_object} 表（FL-4，L3 待审批）落地前，
 * 对象没有数据库归属记录可查，此时键名是<strong>唯一</strong>能表达归属的载体。
 * 带上租户前缀后，下载/删除可校验「键是否属于当前租户」，从而在无表情况下也堵住跨租户越权（FL-1）。
 *
 * <p><b>为何是扁平键而非 {@code {租户}/{年}/{月}/{日}/{uuid}.{ext}}</b>：设计稿 v2 的目录式键含 {@code /}， 而
 * {@code @GetMapping("/{objectName}")} 的 {@code @PathVariable} 只匹配单段路径， 目录式键无法回传；改用 {@code
 * {*objectName}} 又要改动既有路由契约。故本轮采用扁平键 {@code {租户}-{yyyyMMdd}-{uuid}.{ext}}，在不动路由的前提下达成同样的租户隔离强度。
 * 日期分目录可随 FL-4（按库表主键寻址）一并引入。
 */
public final class FileObjectKeyGenerator {

  /** 允许的扩展名白名单（小写）。 */
  private static final Set<String> ALLOWED_EXTENSIONS =
      Set.of(
          "bmp", "csv", "doc", "docx", "gif", "gz", "jpeg", "jpg", "json", "log", "md", "mp4",
          "pdf", "png", "ppt", "pptx", "sql", "svg", "tar", "txt", "webp", "xls", "xlsx", "xml",
          "yaml", "yml", "zip");

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

  private FileObjectKeyGenerator() {}

  /**
   * 生成服务端键：{@code {租户}-{yyyyMMdd}-{uuid}.{ext}}。
   *
   * @param tenantId 当前租户，null 即拒绝（失败关闭，避免把对象写到无主空间）
   * @param originalFilename 客户端原始文件名，仅取扩展名，不参与键名拼接
   */
  public static String generate(Long tenantId, String originalFilename) {
    if (tenantId == null) {
      throw FileErrors.of(FileErrorCodes.TENANT_CONTEXT_MISSING);
    }
    String ext = extensionOf(originalFilename);
    if (ext.isEmpty() || !ALLOWED_EXTENSIONS.contains(ext)) {
      throw FileErrors.of(FileErrorCodes.TYPE_NOT_ALLOWED, ext.isEmpty() ? "(无扩展名)" : ext);
    }
    return tenantId + "-" + LocalDate.now().format(DATE) + "-" + UUID.randomUUID() + "." + ext;
  }

  /** 取小写扩展名；无点或点在最末返回空串。 */
  public static String extensionOf(String filename) {
    if (filename == null || filename.isBlank()) {
      return "";
    }
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) {
      return "";
    }
    return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  /**
   * 校验对象名安全：拒绝路径遍历片段、绝对路径、反斜杠与控制字符。
   *
   * <p>本方法只做「形如路径」的拒绝，归属校验另见 {@link #assertTenantScope(String, Long)}。
   */
  public static void assertSafeName(String objectName) {
    if (objectName == null || objectName.isBlank()) {
      throw FileErrors.of(FileErrorCodes.NAME_INVALID, "空对象名");
    }
    if (objectName.contains("..")
        || objectName.startsWith("/")
        || objectName.contains("\\")
        || objectName.indexOf('\0') >= 0) {
      throw FileErrors.of(FileErrorCodes.NAME_INVALID, objectName);
    }
    for (int i = 0; i < objectName.length(); i++) {
      if (Character.isISOControl(objectName.charAt(i))) {
        throw FileErrors.of(FileErrorCodes.NAME_INVALID, objectName);
      }
    }
  }

  /**
   * 校验对象归属当前租户（FL-1 的 L2 兜底）。
   *
   * <p>v2 目标是「按库表主键寻址 + 比对 {@code file_object.tenant_id}」，依赖 FL-4 的表（L3 未获批）。
   * 在表落地前，租户前缀是唯一可校验的归属载体，故此处按前缀判定，不符即 403。
   */
  public static void assertTenantScope(String objectName, Long tenantId) {
    if (tenantId == null) {
      throw FileErrors.of(FileErrorCodes.TENANT_CONTEXT_MISSING);
    }
    assertSafeName(objectName);
    if (!objectName.startsWith(tenantId + "-")) {
      throw FileErrors.of(FileErrorCodes.ACCESS_DENIED, objectName);
    }
  }

  /**
   * 构造 {@code Content-Disposition}：ASCII 兜底 + RFC 5987 {@code filename*}（FL-3）。
   *
   * <p>直接把客户端提供的名字拼进响应头会形成响应头注入（换行即注入新头）。 这里先剥掉引号与控制字符做 ASCII 兜底，再给出 UTF-8 百分号编码的 {@code
   * filename*}。
   */
  public static String contentDisposition(String originalName) {
    String safe = originalName == null ? "download" : originalName;
    StringBuilder ascii = new StringBuilder();
    for (int i = 0; i < safe.length(); i++) {
      char c = safe.charAt(i);
      ascii.append(c >= 0x20 && c < 0x7F && c != '"' && c != '\\' ? c : '_');
    }
    String encoded =
        URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20").replace("\"", "%22");
    return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
  }
}
