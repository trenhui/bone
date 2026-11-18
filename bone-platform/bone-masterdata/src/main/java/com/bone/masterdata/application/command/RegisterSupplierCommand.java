package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 供应商登记（合并装配）入站命令。
 *
 * <p>与 {@code ResolveAndMergeCommand} 同构：合并裁决完全交给平台 Golden Record 内核，本命令只提供候选源与策略，不携带任何持久化类型。
 */
@Data
public class RegisterSupplierCommand {

  @NotBlank private String mergeId;

  @NotNull private Long tenantId;

  /** 平台统一身份；由上游治理流程分配，不得由本地 ERP/SRM 编码反推。 */
  private String partyId;

  private String sourceSystem;

  private String localId;

  private List<SourceRecordSpec> sources = new ArrayList<>();

  /** fieldCode -> 胜出策略名；未配置字段按 SOURCE_PRIORITY。 */
  private Map<String, String> strategies = new LinkedHashMap<>();

  /** fieldCode -> 指定胜出的 sourceRecordId（STEWARD_OVERRIDE 用）。 */
  private Map<String, String> stewardOverrides = new LinkedHashMap<>();

  /** 具名数据管家账号；STEWARD_OVERRIDE 缺失该项即整笔转人工。 */
  private String stewardAccountId;

  private Instant mergedAt;

  @Data
  public static class SourceRecordSpec {
    @NotBlank private String recordId;
    @NotBlank private String sourceSystem;

    /** 该候选记录自己声明的租户。可空（不声明则视为同租户）；一旦声明且与命令租户不一致， 整笔合并被拒绝且不静默过滤该记录（spec:33-35）。 */
    private Long tenantId;

    private int trustRank;
    private Instant observedAt;
    private Map<String, String> values = new LinkedHashMap<>();
  }
}
