package com.bone.claim.domain.model.rule;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.Instant;
import java.util.Date;
import lombok.Getter;

/**
 * 规则命中记录（§9.5 A3，append-only）。
 *
 * <p><b>为什么是 append-only 而非可变</b>：规则命中是决策执行的<b>事实证据</b>——某条规则在某一刻对某案件
 * 给出了什么结论，事后不可改写（改写等于篡改审计）。「append-only」由<b>代码不提供 UPDATE 路径 + 不授 UPDATE
 * 权限</b>保证，<b>不是</b>由缺列保证：{@link TenantAbstractEntity} 仍注入 {@code updated_at} / {@code updated_by}
 * / {@code deleted}，且本实体在工厂里显式赋值 {@code created_at}/{@code updated_at}（NOT NULL 无默认）， 否则 SDK 的
 * INSERT 列清单会因缺列报 ERROR 1054（0089 同款教训）。无 {@code @Version}：无 UPDATE 路径，乐观锁无意义。
 *
 * <p><b>用途</b>：决策图产出 {@code AUTO_APPROVE} 后，命中了哪些规则（R-AREA/R-DIR/R-DED/R-RAT/R-CAP/R-DEC/
 * R-FAST/R-MAN/R-FRAUD）全部留痕，是「推翻率 / 命中分析 / 人工确认率」指标的事实来源。
 */
@Getter
@Table("t_claim_rule_hit")
public class ClaimRuleHit extends TenantAbstractEntity<Long> {

  /** 规则类别（与决策引擎裁决优先级一致）。 */
  public static final String CATEGORY_AREA = "R-AREA";

  public static final String CATEGORY_DIR = "R-DIR";
  public static final String CATEGORY_DED = "R-DED";
  public static final String CATEGORY_RAT = "R-RAT";
  public static final String CATEGORY_CAP = "R-CAP";
  public static final String CATEGORY_DEC = "R-DEC";
  public static final String CATEGORY_FAST = "R-FAST";
  public static final String CATEGORY_MAN = "R-MAN";
  public static final String CATEGORY_FRAUD = "R-FRAUD";

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "execution_id", nullable = false)
  private Long executionId;

  @Column(name = "rule_code", nullable = false)
  private String ruleCode;

  @Column(name = "rule_version", nullable = false)
  private String ruleVersion;

  @Column(name = "rule_category", nullable = false)
  private String ruleCategory;

  @Column(name = "priority")
  private Integer priority;

  @Column(name = "input_snapshot")
  private String inputSnapshot;

  @Column(name = "conclusion")
  private String conclusion;

  @Column(name = "hit_at", nullable = false)
  private Instant hitAt;

  ClaimRuleHit() {}

  public static ClaimRuleHit create(
      Long tenantId,
      Long claimId,
      Long executionId,
      String ruleCode,
      String ruleVersion,
      String ruleCategory,
      Integer priority,
      String inputSnapshot,
      String conclusion,
      Instant hitAt) {
    ClaimRuleHit hit = new ClaimRuleHit();
    hit.setTenantId(tenantId);
    hit.claimId = claimId;
    hit.executionId = executionId;
    hit.ruleCode = ruleCode == null ? "" : ruleCode;
    hit.ruleVersion = ruleVersion == null ? "" : ruleVersion;
    hit.ruleCategory = ruleCategory == null ? "" : ruleCategory;
    hit.priority = priority == null ? 5 : priority;
    hit.inputSnapshot = inputSnapshot;
    hit.conclusion = conclusion == null ? "" : conclusion;
    hit.hitAt = hitAt == null ? Instant.now() : hitAt;
    Date now = Date.from(hit.hitAt);
    hit.setCreatedAt(now);
    hit.setUpdatedAt(now);
    return hit;
  }
}
