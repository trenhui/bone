package com.bone.tool.codegen.domain.service.renderer;

import java.util.Map;

/**
 * 模板渲染器接口
 * 定义模板渲染的核心方法，便于替换不同的模板引擎实现
 */
public interface TemplateRenderer {
    
    /**
     * 根据模板和上下文参数渲染内容
     * @param templatePath 模板路径
     * @param contextParams 上下文参数
     * @return 渲染后的内容
     */
    String render(String templatePath, Map<String, Object> contextParams);
}