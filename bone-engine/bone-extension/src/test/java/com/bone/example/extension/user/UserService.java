package com.bone.example.extension.user;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.BizContexts;
import com.bone.example.extension.user.greeting.DefaultGreetingExtension;
import com.bone.example.extension.user.greeting.GreetingExtPoint;
import com.bone.example.extension.user.greeting.VipGreetingExtension;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户服务类，使用扩展点机制处理不同类型用户的问候语
 */
public class UserService {

    private final Map<Boolean, GreetingExtPoint> greetingExtensions;
    
    public UserService() {
        // 初始化扩展点实现映射
        greetingExtensions = new HashMap<>();
        greetingExtensions.put(false, new DefaultGreetingExtension());
        greetingExtensions.put(true, new VipGreetingExtension());
    }
    
    /**
     * 欢迎用户，根据用户类型返回不同的问候语
     * @param username 用户名
     * @param isVip 是否为VIP用户
     * @return 问候语
     */
    public String welcomeUser(String username, boolean isVip) {
        // 使用上下文管理器创建并设置上下文（自动清理）
        try (BizContexts.ContextManager manager = BizContexts.with("TENANT_A", "USER_SERVICE")) {
            // 获取当前上下文并设置属性
            @SuppressWarnings("unchecked")
            BizContext<String> context = (BizContext<String>) BizContexts.getCurrent();
            context.setData(username);
            context.withAttribute("username", username);
            context.withAttribute("isVip", isVip);
            // 根据isVip选择对应的扩展实现
            GreetingExtPoint extension = greetingExtensions.get(isVip);
            if (extension == null) {
                extension = greetingExtensions.get(false); // 默认使用普通用户实现
            }
            
            // 调用扩展点方法
            return extension.greet(context);
        }
    }
}