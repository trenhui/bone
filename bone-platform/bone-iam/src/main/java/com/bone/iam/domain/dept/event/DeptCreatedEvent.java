package com.bone.iam.domain.dept.event;

import com.bone.core.domain.DomainEvent;
import com.bone.iam.domain.dept.Dept;
import lombok.Getter;

/** 部门创建领域事件 */
@Getter
public class DeptCreatedEvent implements DomainEvent {
  private final Dept dept;

  public DeptCreatedEvent(Dept dept) {
    this.dept = dept;
  }
}
