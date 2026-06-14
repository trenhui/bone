package com.bone.engine.extension.studio.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.util.StringUtils;

/** 执行日志游标编解码（id 降序翻页）。 */
public final class CursorCodec {

  private static final String PREFIX = "id:";

  private CursorCodec() {}

  public static String encode(Long id) {
    if (id == null) {
      return null;
    }
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString((PREFIX + id).getBytes(StandardCharsets.UTF_8));
  }

  public static Long decode(String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return null;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
      if (raw.startsWith(PREFIX)) {
        return Long.parseLong(raw.substring(PREFIX.length()));
      }
    } catch (IllegalArgumentException ignored) {
      // fall through
    }
    return null;
  }
}
