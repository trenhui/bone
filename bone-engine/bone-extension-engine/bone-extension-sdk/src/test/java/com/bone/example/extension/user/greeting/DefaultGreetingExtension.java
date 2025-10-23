package com.bone.example.extension.user.greeting;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认问候实现
 * 低优先级，作为兜底实现
 */
@Extension(
    name = "默认问候实现",
    description = "通用的默认问候实现，适用于所有用户类型",
    tenantCode = "*",
    priority = 100,
    enabled = true,
    version = "1.0.0"
)
@ExtensionDoc(
    description = "低优先级的默认问候实现，作为所有场景的兜底方案。",
    scenarios = "适用于所有用户类型的通用问候场景",
    implementationDetails = "简单返回标准格式的问候语，包含用户名",
    performance = "测试实现，单次执行耗时<1ms",
    notes = "低优先级，仅在没有其他特定实现时生效",
    author = "测试团队",
    createDate = "2024-01-01"
)
@Service
@Slf4j
public class DefaultGreetingExtension implements GreetingExtPoint {
    @Override
    public String greet(BizContext<String> context) {
        String userName = context.getData();
        return "Hello, " + userName + "!";
    }
}