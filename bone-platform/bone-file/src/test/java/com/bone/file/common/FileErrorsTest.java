package com.bone.file.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.BizException;
import org.junit.jupiter.api.Test;

/** 错误码 → HTTP 状态唯一真源：漏登记即类加载失败，此处验证配对与未知码拒绝。 */
class FileErrorsTest {

  @Test
  void everyKnownCodeMapsToRegisteredStatus() {
    assertThat(FileErrors.httpStatusOf(FileErrorCodes.NOT_FOUND)).isEqualTo(404);
    assertThat(FileErrors.httpStatusOf(FileErrorCodes.ACCESS_DENIED)).isEqualTo(403);
    assertThat(FileErrors.httpStatusOf(FileErrorCodes.NAME_INVALID)).isEqualTo(400);
    assertThat(FileErrors.httpStatusOf(FileErrorCodes.TYPE_NOT_ALLOWED)).isEqualTo(400);
    assertThat(FileErrors.httpStatusOf(FileErrorCodes.TENANT_CONTEXT_MISSING)).isEqualTo(400);
    assertThat(FileErrors.httpStatusOf(FileErrorCodes.UPLOAD_FAILED)).isEqualTo(500);
    assertThat(FileErrors.httpStatusOf(FileErrorCodes.DOWNLOAD_FAILED)).isEqualTo(500);
    assertThat(FileErrors.httpStatusOf(FileErrorCodes.DELETE_FAILED)).isEqualTo(500);
  }

  @Test
  void unknownCodeIsRejectedNotSilentlyDowngraded() {
    assertThatThrownBy(() -> FileErrors.httpStatusOf("FILE_NOT_A_REAL_CODE"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("未登记");
  }

  @Test
  void ofCarriesErrorCodeAndDetailInMessage() {
    BizException e = FileErrors.of(FileErrorCodes.NOT_FOUND, "obj-1");
    assertThat(e.getErrorCode()).isEqualTo(FileErrorCodes.NOT_FOUND);
    assertThat(e.getMessage()).contains("FILE_NOT_FOUND").contains("obj-1");
  }
}
