package com.bone.iam.application.command.handler;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bone.iam.domain.repository.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeletePermissionCommandHandlerTest {

  @Mock PermissionRepository permissionRepository;

  DeletePermissionCommandHandler handler;

  @BeforeEach
  void setUp() {
    handler = new DeletePermissionCommandHandler(permissionRepository);
  }

  @Test
  void deletePermissionCallsRepository() {
    handler.handle(3L);
    verify(permissionRepository, times(1)).deleteById(3L);
  }
}
