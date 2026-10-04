package com.bone.masterdata.domain.model.quality;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 质量检查明细：单条规则对单条记录的判定结果（{@code mdm_qcheck_detail}）。
 *
 * <p>此前该表<b>只有 DDL、没有领域构件与写入链路</b>，导致两个后果：
 *
 * <ol>
 *   <li>质量检查只把逐规则命中数写进 {@code mdm_qcheck_report.report_data} 的 JSON，明细无法按记录检索；
 *   <li>「按记录 ID 查质量结果」在语义上不成立——{@code QualityApplicationService#listQualityResults} 只能 把 recordId
 *       原样回填到 DTO，筛选仍按检查任务走，返回的结果与该记录无关。
 * </ol>
 *
 * <p>本聚合补齐「规则 × 记录」这一最小判定粒度，使按记录过滤成为真实查询而非参数装饰。继承 {@link TenantAggregateRoot} 而非 {@code
 * TenantAbstractEntity}：本表是只追加的子表，租户随 check_id 归属， 时间列名为 {@code checked_at}（无
 * created_at/updated_at/deleted，见 {@code doc/architecture/ddl-required-columns-baseline.json} 的
 * by-design 登记）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("mdm_qcheck_detail")
public class QualityCheckDetail extends TenantAggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "check_id")
  private Long qualityCheckId;

  @Column(name = "rule_id")
  private Long ruleId;

  @Column(name = "record_id")
  private Long recordId;

  /** 是否通过（{@code TINYINT(1) NOT NULL}，显式全列 INSERT 不给值会插入失败，故始终赋值）。 */
  private Boolean passed;

  private String message;

  @Column(name = "checked_at")
  private LocalDateTime checkedAt;

  public static QualityCheckDetail pass(
      Long id, Long qualityCheckId, Long ruleId, Long recordId, String message) {
    return create(id, qualityCheckId, ruleId, recordId, true, message);
  }

  public static QualityCheckDetail fail(
      Long id, Long qualityCheckId, Long ruleId, Long recordId, String message) {
    return create(id, qualityCheckId, ruleId, recordId, false, message);
  }

  private static QualityCheckDetail create(
      Long id, Long qualityCheckId, Long ruleId, Long recordId, boolean passed, String message) {
    QualityCheckDetail detail = new QualityCheckDetail();
    detail.id = id;
    detail.qualityCheckId = qualityCheckId;
    detail.ruleId = ruleId;
    detail.recordId = recordId;
    detail.passed = passed;
    detail.message = message;
    detail.checkedAt = LocalDateTime.now();
    return detail;
  }
}
