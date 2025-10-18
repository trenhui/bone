package com.bone.example.extension;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.Extension;

/**
 * VIP用户问候实现
 * 高优先级，针对VIP用户的特殊问候
 */
@Extension
public class VipGreetingImpl implements GreetingExtPoint {
    @Override
    public String greet(BizContext<String> context) {
        String userName = context.getData();
        return "尊贵的VIP用户 " + userName + "，欢迎回来！";
    }
}