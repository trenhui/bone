package com.bone.file.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 文件对象聚合：归属判定与摘要行为（R8 同型：测试必须调用行为方法）。 */
class FileObjectTest {

  private FileObject anObject() {
    return FileObject.of(
        "id-1", 42L, "platform-files", "42-20260929-abc.pdf", "报表.pdf", "application/pdf", 1024);
  }

  @Test
  void belongsToTenantMatchesOnlyOwnerTenant() {
    FileObject fileObject = anObject();
    assertThat(fileObject.belongsToTenant(42L)).isTrue();
    assertThat(fileObject.belongsToTenant(7L)).isFalse();
    assertThat(fileObject.belongsToTenant(null)).isFalse();
  }

  @Test
  void summarizeDistinguishesOriginalNameWhenDifferent() {
    assertThat(anObject().summarize())
        .contains("42-20260929-abc.pdf")
        .contains("报表.pdf")
        .contains("1024");
  }

  @Test
  void ofRejectsTenantlessObject() {
    assertThatThrownBy(() -> FileObject.of("id-2", null, "b", "k", "n", "text/plain", 1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
