package com.bone.iam.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.tenant.context.TenantContext;
import com.bone.iam.application.command.cmd.CreateDeptCommand;
import com.bone.iam.domain.dept.Dept;
import com.bone.iam.domain.repository.DeptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "CreateDept",
    description = "创建组织机构节点",
    inputSchema =
        "{\"name\": \"string\", \"parentId\": \"long\", \"orderNo\": \"int\", \"status\": \"int\"}",
    outputSchema = "{\"deptId\": \"long\"}",
    idempotent = false,
    cost = 1,
    retryable = true,
    timeout = 5)
@Component
@RequiredArgsConstructor
public class CreateDeptCommandHandler {

  private final DeptRepository deptRepository;

  @Transactional
  public Long handle(CreateDeptCommand cmd) {
    Long tenantId = cmd.getTenantId();
    if (tenantId == null) {
      tenantId = TenantContext.getTenantIdAsLong();
    }
    if (tenantId == null) {
      tenantId = 0L;
    }
    Dept dept =
        Dept.create(cmd.getName(), cmd.getParentId(), cmd.getOrderNo(), cmd.getStatus(), tenantId);
    deptRepository.save(dept);
    return dept.getId();
  }
}
