package com.bone.masterdata.application.query.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 质量结果（对齐前端 qualityResultApi 的 DataQualityResult）。
 *
 * <p><b>粒度约定</b>（由 {@code QualityApplicationService#listQualityResults} 的入参决定）：
 *
 * <ul>
 *   <li>{@code level=DETAIL}：传了 recordId，每行是「某规则 × 某记录」的判定，{@code dataQualityRuleId} 与 {@code
 *       masterDataRecordId} 都有值；
 *   <li>{@code level=SUMMARY}：没传 recordId，每行是一次检查任务的整体结论，{@code dataQualityRuleId} 与 {@code
 *       masterDataRecordId} 均为 {@code null}。
 * </ul>
 *
 * <p>此前 DTO 只有 5 个字段，导致两类信息被塞进同一个位置：汇总行把 checkId 当作"规则 ID"回填（规则列显示的既不是规则也不是记录），而 recordId
 * 无论是否传都被原样回填，看起来像筛过、实际没筛。
 */
@Data
@Builder
public class QualityResultDTO {
  private Long id;

  /** 所属质量检查任务 ID（两种粒度都有值，是串联「任务 → 明细」的稳定锚点）。 */
  private Long qualityCheckId;

  /** 所属主数据实体 ID（汇总行必有；明细行在限定实体查询时才有值）。 */
  private Long masterDataEntityId;

  /** 明细行 = 记录 ID；汇总行 = null。 */
  private Long masterDataRecordId;

  /** 明细行 = 规则 ID；汇总行 = null（汇总不对应单条规则）。 */
  private Long dataQualityRuleId;

  /** 结果粒度：DETAIL / SUMMARY。 */
  private String level;

  private boolean passed;
  private String message;
  private LocalDateTime timestamp;
}
