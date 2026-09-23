package com.bone.engine.extension.studio.application.support;

import java.io.IOException;
import java.io.InputStream;

/** JAR 制品 magic-number 校验（ZIP 本地文件头 PK）。 */
public final class JarMagicValidator {

  private static final byte[] ZIP_LOCAL = {'P', 'K', 0x03, 0x04};
  private static final byte[] ZIP_EMPTY = {'P', 'K', 0x05, 0x06};
  private static final byte[] ZIP_SPANNED = {'P', 'K', 0x07, 0x08};

  private JarMagicValidator() {}

  public static void validate(InputStream in) throws IOException {
    byte[] header = in.readNBytes(4);
    if (!isJarMagic(header)) {
      throw new IllegalArgumentException("文件内容不是有效的 JAR/ZIP 格式（magic-number 校验失败）");
    }
  }

  public static boolean isJarMagic(byte[] header) {
    if (header == null || header.length < 4) {
      return false;
    }
    return matches(header, ZIP_LOCAL) || matches(header, ZIP_EMPTY) || matches(header, ZIP_SPANNED);
  }

  private static boolean matches(byte[] header, byte[] magic) {
    for (int i = 0; i < magic.length; i++) {
      if (header[i] != magic[i]) {
        return false;
      }
    }
    return true;
  }
}
