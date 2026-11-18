package com.bone.procurement.transaction.domain.model.pricing;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * 价格快照哈希计算（纯函数）。
 *
 * <p>哈希输入 = poNo + poVersion + 逐行 itemId/qty/netPrice/lineAmount + taxAmount + currency + fxAsOf +
 * roundingMode + taxCodeVersion。所有 BigDecimal 用 toPlainString 规范化，避免 scale 差异
 * 造成同值不同哈希。快照实体只追加不可变，应用层不提供 update/delete。
 */
public final class PriceSnapshot {

  private PriceSnapshot() {}

  public record LineInput(
      String itemId, BigDecimal quantity, BigDecimal netPrice, BigDecimal lineAmount) {}

  public record Input(
      String poNo,
      int poVersion,
      List<LineInput> lines,
      BigDecimal taxAmount,
      String currency,
      BigDecimal fxRate,
      String fxAsOf,
      String roundingMode,
      String taxCodeVersion) {}

  public static String computeHash(Input in) {
    Objects.requireNonNull(in, "快照输入不可为空");
    StringBuilder sb = new StringBuilder();
    sb.append("poNo=").append(in.poNo()).append('|');
    sb.append("poVersion=").append(in.poVersion()).append('|');
    for (LineInput line : in.lines()) {
      sb.append("line=")
          .append(line.itemId())
          .append(':')
          .append(plain(line.quantity()))
          .append(':')
          .append(plain(line.netPrice()))
          .append(':')
          .append(plain(line.lineAmount()))
          .append('|');
    }
    sb.append("taxAmount=").append(plain(in.taxAmount())).append('|');
    sb.append("currency=").append(in.currency()).append('|');
    sb.append("fxRate=").append(plain(in.fxRate())).append('|');
    sb.append("fxAsOf=").append(in.fxAsOf()).append('|');
    sb.append("roundingMode=").append(in.roundingMode()).append('|');
    sb.append("taxCodeVersion=").append(in.taxCodeVersion());
    return sha256(sb.toString());
  }

  private static String plain(BigDecimal v) {
    return v == null ? "" : v.stripTrailingZeros().toPlainString();
  }

  private static String sha256(String s) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 不可用", e);
    }
  }

  /** 便宜的 toString 仅用于调试，不参与哈希（避免反射导致不稳定）。 */
  @Override
  public String toString() {
    return "PriceSnapshot(immutable)";
  }
}
