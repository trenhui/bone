package com.bone.system.domain.dict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.BizException;
import com.bone.system.domain.model.dict.SysDictItemText;
import com.bone.system.domain.model.dict.valueobject.DictCode;
import org.junit.jupiter.api.Test;

/** 字典项译文聚合纯单测（无容器、无 IO）。 */
class SysDictItemTextTest {

  @Test
  void createKeepsLanguageAndLabel() {
    SysDictItemText text =
        SysDictItemText.create(
            1L,
            0L,
            DictCode.typeCode("sys_status"),
            DictCode.of("ENABLED"),
            "en-US",
            "Enabled",
            "active state");
    assertEquals("en-US", text.getLanguage());
    assertEquals("Enabled", text.getLabel());
    assertEquals("active state", text.getDescription());
  }

  @Test
  void languageIsRequired() {
    assertThrows(
        BizException.class,
        () ->
            SysDictItemText.create(
                2L, 0L, DictCode.typeCode("sys_status"), DictCode.of("ENABLED"), " ", "启用", null));
  }

  @Test
  void labelIsRequired() {
    assertThrows(
        BizException.class,
        () ->
            SysDictItemText.create(
                3L,
                0L,
                DictCode.typeCode("sys_status"),
                DictCode.of("ENABLED"),
                "zh-CN",
                " ",
                null));
  }

  @Test
  void updateKeepsLanguage() {
    SysDictItemText text =
        SysDictItemText.create(
            4L, 0L, DictCode.typeCode("sys_status"), DictCode.of("ENABLED"), "zh-CN", "启用", null);
    text.update("已启用", "说明");
    assertEquals("已启用", text.getLabel());
    assertEquals("说明", text.getDescription());
    assertEquals("zh-CN", text.getLanguage());
  }

  @Test
  void tooLongLanguageIsRejected() {
    assertThrows(
        BizException.class,
        () ->
            SysDictItemText.create(
                5L,
                0L,
                DictCode.typeCode("sys_status"),
                DictCode.of("ENABLED"),
                "zh-CN-VERY-LONG-TAG",
                "启用",
                null));
  }
}
