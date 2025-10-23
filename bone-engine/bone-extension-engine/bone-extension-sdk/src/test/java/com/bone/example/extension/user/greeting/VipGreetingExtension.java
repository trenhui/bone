package com.bone.example.extension.user.greeting;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * VIP用户问候实现
 * 高优先级，当用户为VIP时执行
 */
@Extension(
    name = "VIP用户问候实现",
    description = "为VIP用户提供专属的问候语实现",
    tenantCode = "*",
    priority = 200,
    enabled = true,
    version = "1.0.0"
)
@ExtensionDoc(
    description = "高优先级的VIP用户专属问候实现，提供尊贵的用户体验。",
    scenarios = "VIP用户登录或访问系统时使用",
    implementationDetails = "返回包含VIP标识的特殊问候语，强调用户尊贵身份",
    performance = "测试实现，单次执行耗时<1ms",
    differences = "相比默认实现，增加了VIP标识和更亲切的问候语",
    notes = "高优先级实现，优先于默认实现执行",
    author = "测试团队",
    createDate = "2024-01-01"
)
@Service
@Slf4j
public class VipGreetingExtension implements GreetingExtPoint {
    @Override
    public String greet(BizContext<String> context) {
        String userName = context.getData();
        return "尊贵的VIP用户 " + userName + "，欢迎回来！";
    }
}