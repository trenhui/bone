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
 * 「解析本地编码 → 合并 → 落快照 → 分发」端到端用例的入站命令。
 *
 * <p>{@code partyId} 为空时由 xref 端口解析；解析不到且未给出 {@code partyId} 即 fail-closed。 命令不携带任何 SDK
 * 持久化类型，编排层负责翻译成领域内核的不可变输入。
 */
@Data
public class ResolveAndMergeCommand {

  @NotBlank private String mergeId;

  @NotNull private Long tenantId;

  /** 平台统一身份；由上游治理流程分配，不得由本地 ERP/SRM 编码反推。 */
  private String partyId;

  private String sourceSystem;

  private String localId;

  private List<SourceRecordSpec> sources = new ArrayList<>();

  /** fieldCode -> FieldSurvivorshipRule.Strategy 名称；未配置的字段按 SOURCE_PRIORITY。 */
  private Map<String, String> strategies = new LinkedHashMap<>();

  /** fieldCode -> 指定胜出的 sourceRecordId（STEWARD_OVERRIDE 用）。 */
  private Map<String, String> stewardOverrides = new LinkedHashMap<>();

  private String stewardAccountId;

  /** 为空时取编排时刻；显式给出是为了审计回放确定性。 */
  private Instant mergedAt;

  @Data
  public static class SourceRecordSpec {
    @NotBlank private String recordId;
    @NotBlank private String sourceSystem;
    private int trustRank;
    private Instant observedAt;
    private Map<String, String> values = new LinkedHashMap<>();
  }
}
