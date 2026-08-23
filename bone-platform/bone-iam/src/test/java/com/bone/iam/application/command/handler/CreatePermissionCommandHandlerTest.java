package com.bone.iam.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.iam.application.command.cmd.CreatePermissionCommand;
import com.bone.iam.domain.permission.Permission;
import com.bone.iam.domain.permission.vo.PermissionType;
import com.bone.iam.domain.repository.PermissionRepository;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreatePermissionCommandHandlerTest {

  @Mock PermissionRepository permissionRepository;

  CreatePermissionCommandHandler handler;

  @BeforeEach
  void setUp() {
    handler = new CreatePermissionCommandHandler(permissionRepository);
  }

  @Test
  void createPermissionWithExplicitValues() {
    CreatePermissionCommand cmd = new CreatePermissionCommand();
    cmd.setCode("iam:accounts:read");
    cmd.setName("查看账号");
    cmd.setDescription("读取账号列表");
    cmd.setResourceType("API");
    cmd.setResourcePath("/api/v1/iam/accounts");
    cmd.setAction("GET");
    cmd.setType(PermissionType.OPERATION);
    cmd.setParentId(null);
    cmd.setSortOrder(1);

    when(permissionRepository.save(any(Permission.class)))
        .thenAnswer(
            invocation -> {
              Permission p = invocation.getArgument(0);
              setField(p, "id", 10L);
              return p.getId();
            });

    Long result = handler.handle(cmd);

    assertThat(result).isEqualTo(10L);
    verify(permissionRepository, times(1)).save(any(Permission.class));
  }

  @Test
  void nullDefaultsAreApplied() {
    CreatePermissionCommand cmd = new CreatePermissionCommand();
    cmd.setCode("test:perm");
    cmd.setName("Test");
    cmd.setResourceType(null);
    cmd.setResourcePath(null);
    cmd.setAction(null);
    cmd.setType(null);

    when(permissionRepository.save(any(Permission.class)))
        .thenAnswer(
            invocation -> {
              Permission p = invocation.getArgument(0);
              setField(p, "id", 11L);
              return p.getId();
            });

    Long result = handler.handle(cmd);

    assertThat(result).isEqualTo(11L);
    verify(permissionRepository, times(1)).save(any(Permission.class));
  }

  private static void setField(Object obj, String name, Object value) {
    try {
      Field f = findField(obj.getClass(), name);
      f.setAccessible(true);
      f.set(obj, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
    Class<?> c = cls;
    while (c != null) {
      try {
        return c.getDeclaredField(name);
      } catch (NoSuchFieldException ignored) {
        c = c.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }
}
