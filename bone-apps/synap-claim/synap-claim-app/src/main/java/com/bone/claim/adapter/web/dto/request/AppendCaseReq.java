package com.bone.claim.adapter.web.dto.request;

import com.bone.claim.adapter.web.dto.request.SubmitClaimReq.ClaimItemReq;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;

/**
 * 追加赔案请求（同一报案下的第二笔及以后赔付）。
 *
 * <p><b>刻意不声明保单维度字段</b>（{@code policyNo} / {@code insurerPolicyNo} / {@code planId} / {@code
 * insuredIdHash} / {@code outInsureTime}）：它们一律从报案继承。这些值在事故维度恒定， 允许请求覆盖等于允许把 A 保单的赔付挂到 B 事故上。
 *
 * <p><b>但「没声明的字段」不等于「可以静默忽略」</b>：Spring Boot 默认关闭 {@code FAIL_ON_UNKNOWN_PROPERTIES}，调用方传了 {@code
 * policyNo} 会被无声丢弃并以为覆盖成功。 故用 {@link JsonAnySetter} 捕获所有未知字段，命中保单维度键名即 400 （{@code
 * SC_CLAIM_REPORT_CONTEXT_MISMATCH}）。没有字段可传，也没有字段会被悄悄吞掉。
 */
@Data
@Schema(description = "追加赔案请求（保单/计划/被保人/出险时间一律继承报案）")
public class AppendCaseReq {

  /** 追加赔案时禁止出现的字段名：出现即证明调用方以为可以覆盖事故上下文。 */
  private static final Set<String> FORBIDDEN_CONTEXT_KEYS =
      Set.of("policyNo", "insurerPolicyNo", "planId", "insuredIdHash", "outInsureTime", "reportId");

  private final Map<String, Object> unexpected = new LinkedHashMap<>();

  @Schema(description = "险种（空 = 继承报案）")
  private String claimType;

  @Schema(description = "发票代码（与号码组合防重复赔付）")
  private String invoiceCode;

  @Schema(description = "发票号码")
  private String invoiceNo;

  @Schema(description = "开票日期（发票级「次」分组维度）")
  private LocalDate invoiceDate;

  @Schema(description = "医院编码（发票级分组维度）")
  private String hospitalCode;

  @Schema(description = "病种/诊断编码（发票级分组维度）")
  private String diagnosisCode;

  @Schema(description = "批次号（OSS 路径前缀；不参与编号生成）")
  private String batchNo;

  @Schema(description = "PIPL 单独同意记录号（空 = 该渠道未接同意流水）")
  private String consentRecordId;

  @Schema(description = "承保主体编码（多保司映射解析维度；空 = 跳过解析）")
  private String insurerCode;

  @Schema(description = "费用明细（至少一项）", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotEmpty(message = "追加赔案至少需要一个费用明细项")
  @Valid
  private List<ClaimItemReq> items = new ArrayList<>();

  /** 捕获未声明字段，用于拒绝「以为能覆盖事故上下文」的请求。 */
  @JsonAnySetter
  public void captureUnexpected(String key, Object value) {
    unexpected.put(key, value);
  }

  /**
   * 请求中出现的保单维度字段名（空 = 合法请求）。
   *
   * <p>返回键名而不是布尔值：错误信息要能指名道姓告诉调用方「哪个字段不能传」， 否则对方只会反复试错。
   */
  public List<String> forbiddenContextKeys() {
    return unexpected.keySet().stream().filter(FORBIDDEN_CONTEXT_KEYS::contains).toList();
  }
}
