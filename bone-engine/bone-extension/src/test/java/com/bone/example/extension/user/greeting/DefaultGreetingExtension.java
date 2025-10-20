package com.bone.example.extension.user.greeting;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.Extension;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认问候实现
 * 低优先级，作为兜底实现
 */
@Extension
@Service
@Slf4j
public class DefaultGreetingExtension implements GreetingExtPoint {
    @Override
    public String greet(BizContext<String> context) {
        String userName = context.getData();
        return "Hello, " + userName + "!";
    }
}