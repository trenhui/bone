package com.bone.engine.extension.studio.application.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.BizException;
import com.bone.engine.extension.studio.common.StudioErrorCodes;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class JarMagicValidatorTest {

  @Test
  void acceptsZipLocalHeader() {
    assertDoesNotThrow(
        () ->
            JarMagicValidator.validate(
                new ByteArrayInputStream(new byte[] {0x50, 0x4b, 0x03, 0x04})));
  }

  @Test
  void rejectsNonJarContent() {
    // X-2：非 JAR 内容现在带语义码（EXT_PLUGIN_PACKAGE_INVALID / 400），不再是裸 IllegalArgumentException
    BizException ex =
        assertThrows(
            BizException.class,
            () -> JarMagicValidator.validate(new ByteArrayInputStream("not-a-jar".getBytes())));
    assertEquals(StudioErrorCodes.PLUGIN_PACKAGE_INVALID, ex.getErrorCode());
    assertEquals(400, ex.getCode());
    assertFalse(JarMagicValidator.isJarMagic("text".getBytes()));
  }
}
