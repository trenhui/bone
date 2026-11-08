package com.bone.claim.infrastructure.invoice;

import com.bone.claim.application.port.out.InvoiceVerificationPort;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 受控发票核验适配器（一期）：票面要素确定性核验 + 跨案发票去重。
 *
 * <p>把 R-FRAUD-04（发票金额与清单合计差额 &gt;1 元）与 R-FRAUD-07（同租户同发票只允许一笔赔案） 从「建单时 DB 唯一约束 409 /
 * 人工发现」提前到材料阶段：命中即落 {@code verify_valid=FAIL}， 由既有 R-FRAUD-06 规则与决策图硬闸在放款前拦截，无需扩展 {@code
 * ClaimContextView} 字段。
 *
 * <p><b>不放宽 R-FRAUD-06</b>：未接税局通道时，本地核验通过也只返回 {@code UNVERIFIABLE}（转人工）， 绝不返回 {@code PASS}。返回
 * {@code PASS} 会让一张只通过了格式校验的发票进入快赔——正是本设计要防的假绿。
 */
@Component
@RequiredArgsConstructor
public class ControlledInvoiceVerificationPortAdapter implements InvoiceVerificationPort {

  public static final String CHANNEL = "LOCAL_TICKET_CHECK";
  public static final String REASON_TAX_CHANNEL_MISSING = "TAX_CHANNEL_NOT_CONFIGURED";

  private static final BigDecimal ONE_YUAN = new BigDecimal("1.00");

  private final ClaimCaseRepository claimCaseRepository;

  @Override
  public VerificationResult verify(InvoiceFacts facts) {
    if (facts == null) {
      return VerificationResult.unavailable("INVOICE_FACTS_MISSING");
    }
    if (blank(facts.invoiceCode()) || blank(facts.invoiceNo())) {
      // 关键字段缺失不许默写：宁可转人工，也不拿空票号去查重或判通过。
      return VerificationResult.unavailable("INVOICE_KEY_MISSING");
    }
    if (!facts.invoiceCode().matches("\\d{10,12}")) {
      return VerificationResult.fail("INVOICE_CODE_FORMAT_INVALID", CHANNEL);
    }
    if (!facts.invoiceNo().matches("\\d{8,20}")) {
      return VerificationResult.fail("INVOICE_NO_FORMAT_INVALID", CHANNEL);
    }
    if (facts.invoiceAmount() == null || facts.invoiceAmount().signum() <= 0) {
      return VerificationResult.fail("INVOICE_AMOUNT_INVALID", CHANNEL);
    }
    if (facts.itemTotal() != null
        && facts.invoiceAmount().subtract(facts.itemTotal()).abs().compareTo(ONE_YUAN) > 0) {
      return VerificationResult.fail("INVOICE_ITEM_AMOUNT_MISMATCH", CHANNEL);
    }
    if (duplicateAcrossClaims(facts)) {
      return VerificationResult.fail("DUPLICATE_INVOICE", CHANNEL);
    }
    // 一期无税局通道：本地核验通过 ≠ 已验真，保持转人工（R-FRAUD-06 从严）。
    return VerificationResult.unverifiable(REASON_TAX_CHANNEL_MISSING, CHANNEL);
  }

  /** 同租户同发票号已存在于别的赔案：R-FRAUD-07 的业务前置检查（DB 唯一约束仍是最后防线）。 */
  private boolean duplicateAcrossClaims(InvoiceFacts facts) {
    List<ClaimCase> sameInvoice =
        claimCaseRepository.findByInvoice(facts.tenantId(), facts.invoiceCode(), facts.invoiceNo());
    if (sameInvoice == null) {
      return false;
    }
    return sameInvoice.stream()
        .anyMatch(c -> c != null && !Objects.equals(c.getId(), facts.claimId()));
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
