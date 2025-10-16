package com.bone.tool.codegen.domain.service.generator;

import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.tool.codegen.domain.service.renderer.TemplateRenderer;

import java.util.Map;
import java.util.zip.ZipOutputStream;

/**
 * 代码生成器接口
 * 定义代码生成的核心能力，支持不同的代码生成策略
 */
public interface CodeGenerator {

    /**
     * 生成代码并写入ZIP输出流
     * 
     * @param zipOut ZIP输出流
     * @param codegenTable 代码生成表配置
     * @param modelType 模型类型
     */
    void generateCode(ZipOutputStream zipOut, CodegenTable codegenTable, Integer modelType);

    /**
     * 为主表生成代码
     * 
     * @param zipOut ZIP输出流
     * @param params 上下文参数
     * @param modelType 模型类型
     */
    void generateMainTableCode(ZipOutputStream zipOut, Map<String, Object> params, Integer modelType);

    /**
     * 为子表生成代码
     * 
     * @param zipOut ZIP输出流
     * @param params 上下文参数
     * @param modelType 模型类型
     */
    void generateSubTableCode(ZipOutputStream zipOut, Map<String, Object> params, Integer modelType);

    /**
     * 获取模板渲染器
     * 
     * @return 模板渲染器
     */
    TemplateRenderer getTemplateRenderer();
}