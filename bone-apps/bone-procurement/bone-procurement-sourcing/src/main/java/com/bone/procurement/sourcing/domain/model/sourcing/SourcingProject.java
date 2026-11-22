package com.bone.procurement.sourcing.domain.model.sourcing;

import com.bone.core.annotation.Id;
import com.bone.core.annotation.Version;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 寻源项目聚合根（INV-01 DRAFT 可改，PUBLISHED 后关键字段冻结；INV-02 一个项目至少一个 RFx）。
 *
 * <p>状态迁移集中在 domain：迁移表未列出的事件一律拒绝；终态不可回写。
 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_sourcing_project")
public class SourcingProject extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "project_no")
  private String projectNo;

  @Column(name = "category_id")
  private String categoryId;

  @Column(name = "budget_amount")
  private BigDecimal budgetAmount;

  @Column(name = "currency")
  private String currency;

  @Column(name = "strategy")
  private String strategy;

  @Column(name = "status")
  private String status;

  @Column(name = "owner_id")
  private String ownerId;

  @Column(name = "window_start")
  private Date windowStart;

  @Column(name = "window_end")
  private Date windowEnd;

  @Version
  @Column(name = "version")
  private Integer version;

  public static SourcingProject create(
      Long tenantId,
      String projectNo,
      String categoryId,
      BigDecimal budgetAmount,
      String currency,
      String strategy,
      String ownerId) {
    SourcingProject p = new SourcingProject();
    p.tenantId = tenantId;
    p.projectNo = projectNo;
    p.categoryId = categoryId;
    p.budgetAmount = budgetAmount;
    p.currency = currency;
    p.strategy = strategy;
    p.ownerId = ownerId;
    p.status = SourcingProjectStatus.DRAFT.name();
    p.version = 0;
    Date now = new Date();
    p.setCreatedAt(now);
    p.setUpdatedAt(now);
    return p;
  }

  public SourcingProjectStatus status() {
    return SourcingProjectStatus.valueOf(status);
  }

  /** 发布后项目关键字段冻结（category/budget/currency/strategy/window）。 */
  public void publish() {
    requireTransition(SourcingProjectStatus.PUBLISHED);
    this.status = SourcingProjectStatus.PUBLISHED.name();
  }

  public void closeBidding() {
    requireTransition(SourcingProjectStatus.BIDDING_CLOSED);
    this.status = SourcingProjectStatus.BIDDING_CLOSED.name();
  }

  /** 授标必须人工确认（INV-09）：operator 来自登录态；AI 建议不得直接置 AWARDED。 */
  public void awardByHuman(String operator) {
    if (operator == null || operator.isBlank()) {
      throw new IllegalArgumentException("授标必须具名人工操作人，AI/匿名不得终决");
    }
    requireTransition(SourcingProjectStatus.AWARDED);
    this.status = SourcingProjectStatus.AWARDED.name();
    this.ownerId = operator;
  }

  /** 冻结字段改写守卫：PUBLISHED 之后修改关键字段一律拒绝。 */
  public void changeKeyFields(String newCategoryId, BigDecimal newBudget) {
    if (status().isFrozen()) {
      throw new IllegalStateException("项目已发布，关键字段冻结（INV-01）");
    }
    this.categoryId = newCategoryId;
    this.budgetAmount = newBudget;
  }

  private void requireTransition(SourcingProjectStatus target) {
    if (!status().canTransitionTo(target)) {
      throw new IllegalStateException("非法状态迁移 " + status + " -> " + target + "；终态不可回写");
    }
  }
}
