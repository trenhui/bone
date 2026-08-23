package com.bone.iam.domain.dept;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.iam.domain.dept.event.DeptCreatedEvent;
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
    dept.addDomainEvent(new DeptCreatedEvent(dept));
    return dept;
  }

  public void update(String name, Long parentId, Integer orderNo, Integer status) {
    this.name = name;
    this.parentId = parentId;
    this.orderNo = orderNo;
    this.status = status;
    this.updatedAt = LocalDateTime.now();
  }
}
