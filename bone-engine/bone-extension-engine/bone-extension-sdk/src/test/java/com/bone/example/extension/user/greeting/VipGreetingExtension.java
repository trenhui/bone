package com.bone.example.extension.user.greeting;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * VIP用户问候实现
 * 高优先级，当用户为VIP时执行
 */
@Extension(name = "vipGreeting", priority = 100)
@Service
@Slf4j
public class VipGreetingExtension implements GreetingExtPoint {
    @Override
    public String greet(BizContext<String> context) {
        String userName = context.getData();
        return "尊贵的VIP用户 " + userName + "，欢迎回来！";
    }
}