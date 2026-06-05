package com.bone.metadata.catalog.common;

import java.util.Optional;
import org.springframework.util.StringUtils;

/** catalog HTTP 横切：If-Match 解析、ETag 格式化。 */
public final class CatalogHttpSupport {

  private CatalogHttpSupport() {}

  public static Optional<Integer> parseIfMatchVersion(String ifMatch) {
    if (!StringUtils.hasText(ifMatch)) {
      return Optional.empty();
    }
    String token = ifMatch.trim();
    if (token.startsWith("W/")) {
      token = token.substring(2).trim();
    }
    if (token.startsWith("\"") && token.endsWith("\"") && token.length() >= 2) {
      token = token.substring(1, token.length() - 1);
    }
    if (token.startsWith("v") || token.startsWith("V")) {
      token = token.substring(1);
    }
    try {
      return Optional.of(Integer.parseInt(token));
    } catch (NumberFormatException ex) {
      return Optional.empty();
    }
  }

  public static String formatEtag(Integer version) {
    return version == null ? null : "\"v" + version + "\"";
  }
}
