package com.bone.engine.extension.studio.application.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    assertThrows(
        IllegalArgumentException.class,
        () -> JarMagicValidator.validate(new ByteArrayInputStream("not-a-jar".getBytes())));
    assertFalse(JarMagicValidator.isJarMagic("text".getBytes()));
  }
}
