package com.bone.iam.application;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bone.iam.domain.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** RoleApplicationService#delete 单元测试（原 DeleteRoleCommandHandler 逻辑已内联）。 */
@ExtendWith(MockitoExtension.class)
class RoleApplicationServiceDeleteTest {

  @Mock RoleRepository roleRepository;

  @InjectMocks RoleApplicationService roleApplicationService;

  @Test
  void deleteRoleCallsRepository() {
    roleApplicationService.delete(5L);
    verify(roleRepository, times(1)).deleteById(5L);
  }
}
