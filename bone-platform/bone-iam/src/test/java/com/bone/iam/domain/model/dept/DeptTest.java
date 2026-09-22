package com.bone.iam.domain.model.dept;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.bone.iam.domain.model.dept.event.DeptCreatedEvent;
import org.junit.jupiter.api.Test;

/** {@link Dept} 纯单测：部门创建默认值、事件发布与更新（无容器）。 */
class DeptTest {

  @Test
  void testCreateDefaultsOrderAndStatus() {
    Dept dept = Dept.create("研发部", 0L, null, null, 1L);

    assertEquals("研发部", dept.getName());
    assertEquals(0L, dept.getParentId());
    assertEquals(0, dept.getOrderNo());
    assertEquals(1, dept.getStatus());
    assertEquals(1L, dept.getTenantId());
    assertEquals(1, dept.getDomainEvents().size());
    assertInstanceOf(DeptCreatedEvent.class, dept.getDomainEvents().get(0));
  }

  @Test
  void testUpdateOverridesDeptProfile() {
    Dept dept = Dept.create("研发部", 0L, 1, 1, 1L);

    dept.update("研发一部", 10L, 2, 0);

    assertEquals("研发一部", dept.getName());
    assertEquals(10L, dept.getParentId());
    assertEquals(2, dept.getOrderNo());
    assertEquals(0, dept.getStatus());
  }
}
