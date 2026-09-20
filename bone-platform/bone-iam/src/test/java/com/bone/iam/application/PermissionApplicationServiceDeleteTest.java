package com.bone.iam.application;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bone.iam.domain.repository.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** PermissionApplicationService#delete 单元测试（原 DeletePermissionCommandHandler 逻辑已内联）。 */
@ExtendWith(MockitoExtension.class)
class PermissionApplicationServiceDeleteTest {

  @Mock PermissionRepository permissionRepository;

  @InjectMocks PermissionApplicationService permissionApplicationService;

  @Test
  void deletePermissionCallsRepository() {
    permissionApplicationService.delete(3L);
    verify(permissionRepository, times(1)).deleteById(3L);
  }
}
