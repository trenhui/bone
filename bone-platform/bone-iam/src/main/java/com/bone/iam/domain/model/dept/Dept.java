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

  @Id

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
  @Deleted
  private Boolean deleted = false;

  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private String name;
  private Long parentId;
  private Integer orderNo;
  private Integer status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static Dept create(
      String name, Long parentId, Integer orderNo, Integer status, Long tenantId) {
    Dept dept = new Dept();
    dept.name = name;
    dept.parentId = parentId;
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
}
