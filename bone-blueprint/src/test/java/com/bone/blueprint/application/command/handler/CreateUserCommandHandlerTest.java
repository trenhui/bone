package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CreateUserCommand;
import com.bone.blueprint.domain.model.user.User;
import com.bone.blueprint.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class CreateUserCommandHandlerTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private CreateUserCommandHandler createUserCommandHandler;
    
    @Test
    public void testHandle() {
        // 构建创建用户命令
        CreateUserCommand command = CreateUserCommand.builder()
            .username("testuser")
            .password("password123")
            .email("test@example.com")
            .build();
        
        // 执行命令
        createUserCommandHandler.handle(command);
        
        // 验证用户仓库的save方法被调用了一次
        verify(userRepository, times(1)).save(org.mockito.ArgumentMatchers.any(User.class));
    }
}
