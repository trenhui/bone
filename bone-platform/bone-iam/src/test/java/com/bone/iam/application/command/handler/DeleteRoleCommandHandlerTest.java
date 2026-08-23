package com.bone.iam.application.command.handler;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bone.iam.domain.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteRoleCommandHandlerTest {

  @Mock RoleRepository roleRepository;

  DeleteRoleCommandHandler handler;

  @BeforeEach
  void setUp() {
    handler = new DeleteRoleCommandHandler(roleRepository);
  }

  @Test
  void deleteRoleCallsRepository() {
    handler.handle(5L);
    verify(roleRepository, times(1)).deleteById(5L);
  }
}
