package com.bone.tool.codegen.domain.service;

import com.bone.tool.codegen.application.dto.CodegenTablePageRequest;
import com.bone.tool.codegen.application.dto.CodegenTableRequest;
import com.bone.tool.codegen.application.dto.GenerateCustomCodeRequest;
import com.bone.tool.codegen.application.dto.CodegenDetailResponse;
import com.bone.tool.codegen.application.dto.CodegenTableResponse;
import java.io.OutputStream;
import java.util.List;

/**
 * 代码生成服务接口
 * 定义代码生成相关的所有公共服务方法
 */
public interface CodegenServiceInterface {
    
    /**
     * 生成自定义代码
     * @param request 代码生成请求参数对象
     * @param outputStream 输出流
     */
    void generateCustomCode(GenerateCustomCodeRequest request, OutputStream outputStream);
    
    /**
     * 生成自定义代码（单参数版本，用于测试兼容）
     * @param request 代码生成请求参数对象
     * @return 字节数组
     */
    byte[] generateCustomCode(GenerateCustomCodeRequest request);
    
    /**
     * 获取代码生成详情
     * @param tableId 表ID
     * @return 详情响应对象
     */
    CodegenDetailResponse getCodegenDetail(Long tableId);
    
    /**
     * 删除表
     * @param tableId 表ID
     */
    void deleteTable(Long tableId);
    
    /**
     * 批量生成代码
     * @param tableIds 表ID列表
     * @param templateCode 模板代码
     * @param modelType 模型类型
     * @param outputStream 输出流
     */
    void generateBatchCodes(List<Long> tableIds, String templateCode, Integer modelType, OutputStream outputStream);
    
    /**
     * 更新代码生成表
     * @param request 更新请求对象
     */
    void updateCodegenTable(CodegenTableRequest request);
    
    /**
     * 获取代码生成表分页响应
     * @param request 分页请求对象
     * @return 分页响应对象
     */
    CodegenTableResponse getCodegenTablePageResponse(CodegenTablePageRequest request);
    
    /**
     * 导入表结构从数据库
     * @param datasourceId 数据源ID
     * @param tableNames 表名列表
     * @param moduleName 模块名
     * @param packageName 包名
     * @param sceneType 场景类型
     * @param modelType 模型类型
     * @return 导入的表ID列表
     */
    List<Long> importTablesFromDatabase(Long datasourceId, List<String> tableNames, String moduleName, 
                                        String packageName, Integer sceneType, Integer modelType);
    
    /**
     * 同步表结构从数据库
     * @param tableId 表ID
     */
    void syncTableFromDatabase(Long tableId);
}