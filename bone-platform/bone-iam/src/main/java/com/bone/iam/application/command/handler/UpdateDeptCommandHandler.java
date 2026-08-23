package com.bone.iam.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.iam.application.command.cmd.UpdateDeptCommand;
import com.bone.iam.domain.dept.Dept;
import com.bone.iam.domain.repository.DeptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "UpdateDept",
    description = "更新组织机构节点",
    inputSchema =
        "{\"id\": \"long\", \"name\": \"string\", \"parentId\": \"long\", \"orderNo\": \"int\", \"status\": \"int\"}",
    outputSchema = "{\"deptId\": \"long\"}",
    idempotent = false,
    cost = 1,
    retryable = true,
    timeout = 5)
@Component
@RequiredArgsConstructor
public class UpdateDeptCommandHandler {

  private final DeptRepository deptRepository;

  @Transactional
  public Long handle(UpdateDeptCommand cmd) {
    Dept dept = deptRepository.findById(cmd.getId());
    if (dept == null) {
      throw new IllegalArgumentException("部门不存在: " + cmd.getId());
    }
    dept.update(cmd.getName(), cmd.getParentId(), cmd.getOrderNo(), cmd.getStatus());
    deptRepository.update(dept);
    return dept.getId();
  }
}
