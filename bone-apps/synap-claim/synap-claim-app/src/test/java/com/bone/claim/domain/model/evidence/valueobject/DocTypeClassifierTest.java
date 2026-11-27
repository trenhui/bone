package com.bone.claim.domain.model.evidence.valueobject;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.domain.model.evidence.valueobject.DocTypeClassifier.ClassificationInput;
import com.bone.claim.domain.model.evidence.valueobject.DocTypeClassifier.ClassificationResult;
import com.bone.claim.domain.model.evidence.valueobject.DocTypeClassifier.ImageSignals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 融合分类器：文件名语义 + 内容版式信号 + 可选元数据/OCR，证据不足落 OTHER。 */
class DocTypeClassifierTest {

  private static ImageSignals signatureSignals() {
    // 实测 TPA 签名影像：inkRatio=0.097, concentration=0.60, tableLine=0
    return new ImageSignals(0.097, 0.60, 0.0, null);
  }

  private static ImageSignals invoiceSignals() {
    // 实测票据影像：表格线明显、墨迹分散
    return new ImageSignals(0.203, 0.20, 0.067, null);
  }

  @Test
  @DisplayName("身份证/银行卡按中英文别名识别")
  void identityAndBankCard() {
    assertThat(DocTypeClassifier.classify("7-身份证-正面-00.jpg")).isEqualTo(DocType.ID_CARD);
    assertThat(DocTypeClassifier.classify("ID_CARD_front.png")).isEqualTo(DocType.ID_CARD);
    assertThat(DocTypeClassifier.classify("银行卡反面.jpg")).isEqualTo(DocType.BANK_CARD);
    assertThat(DocTypeClassifier.classify("bank-card-1234.png")).isEqualTo(DocType.BANK_CARD);
  }

  @Test
  @DisplayName("结算单优先于费用/票据宽泛词，避免误归发票或清单")
  void settlementWinsOverGenericTerms() {
    assertThat(DocTypeClassifier.classify("0-北京-跨省异地就医住院费用结算单.jpg"))
        .isEqualTo(DocType.MEDICAL_SETTLEMENT);
    assertThat(DocTypeClassifier.classify("医保结算单.pdf")).isEqualTo(DocType.MEDICAL_SETTLEMENT);
  }

  @Test
  @DisplayName("修复：收费单归费用清单，只有票据/发票才归发票")
  void chargeSlipIsListNotInvoice() {
    assertThat(DocTypeClassifier.classify("1-汕头口腔医疗中心-收费单.jpg")).isEqualTo(DocType.LIST);
    assertThat(DocTypeClassifier.classify("费用清单.pdf")).isEqualTo(DocType.LIST);
    assertThat(DocTypeClassifier.classify("5-浙江省医疗门诊收费票据_02.png")).isEqualTo(DocType.INVOICE);
    assertThat(DocTypeClassifier.classify("电子发票.pdf")).isEqualTo(DocType.INVOICE);
  }

  @Test
  @DisplayName("签名图：hash 文件名无语义，靠内容版式识别为签名，不误判为发票")
  void signatureByContentSignals() {
    ClassificationInput input =
        new ClassificationInput(
            "1CA9AC0A80B895BFF0524A577696E054.jpg", "3017", signatureSignals(), null);
    ClassificationResult result = DocTypeClassifier.classifyDetailed(input);
    assertThat(result.docType()).isEqualTo(DocType.SIGNATURE);
    // 内容信号(4.0) 压过 imageType=3017 的发票弱证据(1.0)
    assertThat(result.explain()).contains("签名");
    assertThat(result.score()).isGreaterThan(result.margin());
  }

  @Test
  @DisplayName("同名 hash 图若版式是票据，则归发票而非签名（信号对比钉子）")
  void sameHashNameWithInvoiceSignals() {
    ClassificationInput input =
        new ClassificationInput("1CA9AC0A.jpg", null, invoiceSignals(), null);
    // 表格线+分散墨迹给 INVOICE/LIST 各 1.2，并列且低于阈值 → fail-closed 落 OTHER，
    // 绝不因为 "imageType=发票" 就臆断；这里断言不误判为 SIGNATURE。
    assertThat(DocTypeClassifier.classifyDetailed(input).docType()).isNotEqualTo(DocType.SIGNATURE);
  }

  @Test
  @DisplayName("文件名含签名关键词命中签名类型")
  void signatureByFilename() {
    assertThat(DocTypeClassifier.classify("理赔申请书-签名页.jpg")).isEqualTo(DocType.SIGNATURE);
  }

  @Test
  @DisplayName("未命中与空文件名落 OTHER，解释原因可追溯")
  void unknownFallsBackToOther() {
    assertThat(DocTypeClassifier.classify("3-泰瑞人寿保险-理赔申请书.jpg")).isEqualTo(DocType.OTHER);
    assertThat(DocTypeClassifier.classify(null)).isEqualTo(DocType.OTHER);
    assertThat(DocTypeClassifier.classify("")).isEqualTo(DocType.OTHER);
    assertThat(DocTypeClassifier.explain("身份证正面.jpg")).contains("文件名命中");
    assertThat(DocTypeClassifier.explain("无规则文件.bin")).contains("无有效证据");
    assertThat(DocTypeClassifier.explain((String) null)).contains("无有效证据");
  }
}
