package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.iam.application.command.UpdateRoleCommand;
import com.bone.iam.application.support.TenantQuotaEnforcer;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.model.role.Role;
import com.bone.iam.domain.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code RoleApplicationService#update} 单元测试：守卫「编辑角色」的改名必须落库。
 *
 * <p><b>实测缺陷：</b>converter 已经把 {@code UpdateRoleCommand#name} 带过来了，但 service 只调用了 {@code
 * role.update(description)} —— 结果 {@code PUT /api/v1/iam/roles/{id}} 返回 <b>200</b>， UI
 * 提示「保存成功」，回读名字却还是旧的。这是「200 但改动静默丢弃」最典型的形态， 单看 HTTP 状态码完全测不出来，必须对落库实体做断言。
 *
 * <p><b>负向探针：</b>把 service 里的 {@code role.update(cmd.getName(), cmd.getDescription());} 退回 {@code
 * role.update(cmd.getDescription());}，本类必须变红。
 */
@ExtendWith(MockitoExtension.class)
class RoleApplicationServiceUpdateTest {

  @Mock RoleRepository roleRepository;
  @Mock TenantQuotaEnforcer tenantQuotaEnforcer;
  @Mock TenantProvider tenantProvider;

  @InjectMocks RoleApplicationService roleApplicationService;

  @Test
  void updateAppliesBothNameAndDescription() {
    Role existing = Role.create("旧角色名", "ROLE_OLD", "旧描述", 1, 0L, null);
    when(roleRepository.findById(9L)).thenReturn(existing);

    UpdateRoleCommand cmd = new UpdateRoleCommand();
    cmd.setId(9L);
    cmd.setName("新角色名");
    cmd.setDescription("新描述");

    roleApplicationService.update(cmd);

    ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
    verify(roleRepository).update(captor.capture());
    Role persisted = captor.getValue();

    assertThat(persisted.getName()).isEqualTo("新角色名");
    assertThat(persisted.getDescription()).isEqualTo("新描述");
  }

  /** null 表示「本次不改该字段」（PATCH 语义），不能把已有值抹成 null。 */
  @Test
  void updateKeepsNameWhenNameIsNull() {
    Role existing = Role.create("保持不变", "ROLE_KEEP", "旧描述", 1, 0L, null);
    when(roleRepository.findById(10L)).thenReturn(existing);

    UpdateRoleCommand cmd = new UpdateRoleCommand();
    cmd.setId(10L);
    cmd.setName(null);
    cmd.setDescription("只改描述");

    roleApplicationService.update(cmd);

    ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
    verify(roleRepository).update(captor.capture());

    assertThat(captor.getValue().getName()).isEqualTo("保持不变");
    assertThat(captor.getValue().getDescription()).isEqualTo("只改描述");
  }

  /** 角色不存在必须报错，而不是静默「更新成功」。 */
  @Test
  void updateRejectsMissingRole() {
    when(roleRepository.findById(404L)).thenReturn(null);

    UpdateRoleCommand cmd = new UpdateRoleCommand();
    cmd.setId(404L);
    cmd.setName("whatever");

    org.junit.jupiter.api.Assertions.assertThrows(
        RuntimeException.class, () -> roleApplicationService.update(cmd));
    verify(roleRepository, org.mockito.Mockito.never()).update(any(Role.class));
  }
}
