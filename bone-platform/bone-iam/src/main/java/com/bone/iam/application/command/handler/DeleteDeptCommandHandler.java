package com.bone.iam.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.iam.application.command.cmd.DeleteDeptCommand;
import com.bone.iam.domain.repository.DeptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "DeleteDept",
    description = "删除组织机构节点",
    inputSchema = "{\"id\": \"long\"}",
    outputSchema = "{\"id\": \"long\"}",
    idempotent = false,
    cost = 1,
    retryable = false,
    timeout = 5)
@Component
@RequiredArgsConstructor
public class DeleteDeptCommandHandler {

  private final DeptRepository deptRepository;

  @Transactional
  public Long handle(DeleteDeptCommand cmd) {
    DeptRepository repo = deptRepository;
    if (repo.findById(cmd.getId()) == null) {
      throw new IllegalArgumentException("部门不存在: " + cmd.getId());
    }
    repo.deleteById(cmd.getId());
    return cmd.getId();
  }
}
