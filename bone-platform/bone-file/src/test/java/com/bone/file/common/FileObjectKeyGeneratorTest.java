package com.bone.file.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 对象键生成器：租户前缀、扩展名白名单、路径遍历拒绝、响应头注入防护。 */
class FileObjectKeyGeneratorTest {

  @Test
  @DisplayName("generate：键含租户前缀与日期段，扩展名取自原始文件名")
  void generatePrefersTenantPrefixAndSafeExtension() {
    String key = FileObjectKeyGenerator.generate(42L, "季度报表.xlsx");
    assertThat(key).startsWith("42-").contains("-").endsWith(".xlsx");
    assertThat(key).doesNotContain("季度报表");
  }

  @Test
  @DisplayName("generate：无租户上下文即失败关闭（FILE_TENANT_CONTEXT_MISSING）")
  void generateFailsClosedWithoutTenant() {
    assertThatThrownBy(() -> FileObjectKeyGenerator.generate(null, "a.pdf"))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(FileErrorCodes.TENANT_CONTEXT_MISSING));
  }

  @Test
  @DisplayName("generate：扩展名不在白名单即拒绝（FILE_TYPE_NOT_ALLOWED）")
  void generateRejectsDisallowedExtension() {
    assertThatThrownBy(() -> FileObjectKeyGenerator.generate(1L, "payload.exe"))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(FileErrorCodes.TYPE_NOT_ALLOWED));
  }

  @Test
  @DisplayName("assertSafeName：路径遍历 / 绝对路径 / 反斜杠 / 控制字符全部拒绝")
  void assertSafeNameRejectsTraversalShapes() {
    for (String bad : new String[] {"../etc/passwd", "/abs/path", "a\\b", "x\ty", ""}) {
      assertThatThrownBy(() -> FileObjectKeyGenerator.assertSafeName(bad))
          .as("objectName=%s", bad)
          .isInstanceOf(BizException.class)
          .satisfies(
              e ->
                  assertThat(((BizException) e).getErrorCode())
                      .isEqualTo(FileErrorCodes.NAME_INVALID));
    }
  }

  @Test
  @DisplayName("assertTenantScope：键不带当前租户前缀即 403（FL-1 归属兜底）")
  void assertTenantScopeRejectsForeignObject() {
    assertThatThrownBy(() -> FileObjectKeyGenerator.assertTenantScope("7-20260929-x.pdf", 42L))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(FileErrorCodes.ACCESS_DENIED));
    // 同租户放行。
    FileObjectKeyGenerator.assertTenantScope("42-20260929-x.pdf", 42L);
  }

  @Test
  @DisplayName("contentDisposition：换行 / 引号不进响应头，UTF-8 名走 RFC 5987 filename*")
  void contentDispositionNeutralizesHeaderInjection() {
    String evil = "a\"b\r\nX-Injected: 1";
    String header = FileObjectKeyGenerator.contentDisposition(evil);
    assertThat(header).startsWith("attachment; filename=\"");
    // 输入中的引号被替换为 _，仅剩包装用的成对引号；无 CRLF，注入面清零。
    assertThat(header).doesNotContain("\r").doesNotContain("\n");
    assertThat(header.chars().filter(c -> c == '"').count()).isEqualTo(2L);
    assertThat(header).contains("filename*=UTF-8''");
    assertThat(FileObjectKeyGenerator.contentDisposition("报表.pdf"))
        .contains("filename*=UTF-8''%E6%8A%A5%E8%A1%A8.pdf");
  }
}
