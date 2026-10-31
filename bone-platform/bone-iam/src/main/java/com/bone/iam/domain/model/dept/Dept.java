package com.bone.iam.domain.model.dept;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_dept")
public class Dept extends TenantAggregateRoot<Long> {

  /**
   * 逻辑删除标记。
   *
   * <p><b>为何必须显式声明</b>：bone-metadata-sdk 的 {@code TableMetadata#isSoftDeletable()} 判据是 <b>实体内是否存在带
   * {@link com.bone.core.annotation.Deleted} 的字段</b>，与 DDL 有无 {@code deleted} 列无关。 {@code
   * TenantAggregateRoot} 只提供 {@code tenantId}，不像 {@code AbstractEntity} 那样自带该字段； 本类若不声明，{@code
   * Repository#deleteById} 会发出 {@code DELETE FROM} —— 删除即整行物理消失、不可审计不可恢复。
   *
   * <p>本表<b>无唯一索引</b>，故恢复软删不存在「同值无法重建」冲突（见soft-delete-declaration-baseline.json 的 {@code
   * _uk_conflict} 段）。
   */
  // 必须紧贴字段：门禁 DELETED_FIELD_RE 要求「@Deleted 行 + 紧邻一行 private 声明」，
  // 把注解放到注解与字段之间会让这个声明对门禁不可见，误报成软删缺口（2026-10-06 踩坑）。
  @Deleted private Boolean deleted = false;

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private String name;
  private Long parentId;

  /**
   * 部门负责人（审批流 {@code DEPT_HEAD} 指派策略的数据源）。跨聚合只存账号 ID，不做对象引用。
   *
   * <p><b>与 {@code Account.managerId} 的分工</b>：负责人是「部门这个组织单元向谁负责」，主管是「这个人向谁汇报」。
   * 审批场景两者都要——报销先找直属主管（不论部门），采购/用章找部门负责人（不论个人主管）。用一个字段兼任会让 「同一部门内所有人共用一位负责人」与「每个人有自己的主管」这两种事实互相覆盖。
   *
   * <p>可为空：小部门未指定负责人时留空，由引擎按失败关闭<b>挂起</b>而不是跳过审批节点。
   */
  private Long leaderId;

  private Integer orderNo;
  private Integer status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static Dept create(
      String name, Long parentId, Integer orderNo, Integer status, Long tenantId) {
    return create(name, parentId, null, orderNo, status, tenantId);
  }

  /**
   * 创建部门并指定负责人。
   *
   * <p>与 5 参重载并存（而非替换签名）：后者有 6 处调用点（1 主 + 5 测试），改签名会让一次「加了个可空字段」的改动 波及一堆无关测试。可空字段用重载扩展，是这类改动的通用做法。
   */
  public static Dept create(
      String name, Long parentId, Long leaderId, Integer orderNo, Integer status, Long tenantId) {
    Dept dept = new Dept();
    dept.name = name;
    dept.parentId = parentId;
    dept.leaderId = leaderId;
    dept.orderNo = orderNo == null ? 0 : orderNo;
    dept.status = status == null ? 1 : status;
    dept.setTenantId(tenantId);
    dept.createdAt = LocalDateTime.now();
    dept.updatedAt = LocalDateTime.now();
    return dept;
  }

  public void update(String name, Long parentId, Integer orderNo, Integer status) {
    this.name = name;
    this.parentId = parentId;
    this.orderNo = orderNo;
    // HTTP 契约（UpdateDeptReq）不含 status：未提供时保留现有值，避免全列 UPDATE 把非空列写成 NULL
    if (status != null) {
      this.status = status;
    }
    this.updatedAt = LocalDateTime.now();
  }

  /**
   * 调整部门负责人。{@code null} 表示清空（小部门可暂无负责人）。
   *
   * <p>为什么不并进 {@link #update}：那里的 status 用「null = 保留原值」语义，若 leaderId 照抄， 「清空负责人」就永远做不到——这正是 {@code
   * Account.deptId} 三分支语义最容易被误抄的地方。独立的 {@code changeXxx} 方法让「不动」与「清空」由「调不调这个方法」区分，语义无歧义。
   */
  public void changeLeader(Long newLeaderId) {
    if (java.util.Objects.equals(this.leaderId, newLeaderId)) {
      return;
    }
    this.leaderId = newLeaderId;
    this.updatedAt = LocalDateTime.now();
  }
}
