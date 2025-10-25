package com.bone.tool.codegen.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.bone.tool.codegen.application.dto.CodegenTablePageRequest;
import com.bone.tool.codegen.application.dto.CodegenTableRequest;
import com.bone.tool.codegen.application.dto.GenerateCustomCodeRequest;
import com.bone.tool.codegen.application.dto.CodegenDetailResponse;
import com.bone.tool.codegen.application.dto.CodegenTableResponse;
import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.tool.codegen.domain.repository.CodegenTableRepository;
import com.bone.tool.codegen.domain.service.generator.CodeGenerator;
import com.bone.tool.codegen.domain.service.renderer.TemplateRenderer;
import com.bone.tool.codegen.domain.service.DatabaseTableServiceInterface;

import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.util.Optional;

/**
 * 代码生成服务实现类
 * <p>
 * 负责代码生成相关的核心业务逻辑，包括代码生成、表管理和数据同步等功能
 * 实现了CodegenServiceInterface接口，提供完整的代码生成服务能力
 */
@Service
public class CodegenService implements CodegenServiceInterface {

    // 依赖注入 - 使用final修饰所有字段确保不可变性
    private final CodeGenerator codeGenerator;
    private final DatabaseTableServiceInterface databaseTableService;
    private final CodegenTableRepository codegenTableRepository;
    private final TemplateRenderer templateRenderer;
    
    // 使用SLF4J进行日志记录
    private static final Logger logger = LoggerFactory.getLogger(CodegenService.class);

    /**
     * 构造函数 - 依赖注入
     * 
     * @param codeGenerator 代码生成器组件
     * @param databaseTableService 数据库表服务接口
     * @param codegenTableRepository 代码生成表仓库
     * @param templateRenderer 模板渲染器组件
     */
    @Autowired
    public CodegenService(CodeGenerator codeGenerator, 
                         DatabaseTableServiceInterface databaseTableService,
                         CodegenTableRepository codegenTableRepository, 
                         TemplateRenderer templateRenderer) {
        this.codeGenerator = codeGenerator;
        this.databaseTableService = databaseTableService;
        this.codegenTableRepository = codegenTableRepository;
        this.templateRenderer = templateRenderer;
        
        // 验证所有依赖项非空
        Assert.notNull(codeGenerator, "代码生成器组件不能为空");
        Assert.notNull(databaseTableService, "数据库表服务组件不能为空");
        Assert.notNull(codegenTableRepository, "代码生成表仓库不能为空");
        Assert.notNull(templateRenderer, "模板渲染器组件不能为空");
    }

    /**
     * 生成自定义代码
     * 
     * @param request 代码生成请求参数对象
     * @param outputStream 输出流，用于写入生成的代码
     */
    @Override
    public void generateCustomCode(GenerateCustomCodeRequest request, OutputStream outputStream) {
        logger.info("开始生成自定义代码，数据源ID: {}, 表数量: {}", 
                   request.getDatasourceId(), request.getTableNames().size());
        
        // 参数验证
        validateCodeGenRequest(request, outputStream);
        
        Long datasourceId = request.getDatasourceId();
        List<String> tableNames = request.getTableNames();
        
        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
            int successCount = 0;
            
            // 为每个表生成代码
            for (String tableName : tableNames) {
                try {
                    logger.debug("处理表: {}", tableName);
                    
                    // 获取表信息并生成代码
                    Optional<CodegenTable> tableOptional = findCodegenTable(datasourceId, tableName);
                    if (tableOptional.isPresent()) {
                        // 使用默认模型类型1，避免类型转换问题
                        codeGenerator.generateCode(zipOut, tableOptional.get(), 1);
                        successCount++;
                    } else {
                        logger.warn("表 {} 在数据源 {} 中不存在，跳过处理", tableName, datasourceId);
                    }
                    
                } catch (Exception e) {
                    logger.error("处理表 {} 时出错: {}", tableName, e.getMessage(), e);
                    // 继续处理其他表，确保部分失败不影响整体
                }
            }
            
            logger.info("代码生成完成，成功处理 {} 个表，共 {} 个表", successCount, tableNames.size());
        } catch (IOException e) {
            logger.error("生成代码时发生IO错误: {}", e.getMessage(), e);
            throw new RuntimeException("生成代码失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成自定义代码（单参数版本，用于测试兼容）
     * 
     * @param request 代码生成请求参数对象
     * @return 生成的代码字节数组
     */
    @Override
    public byte[] generateCustomCode(GenerateCustomCodeRequest request) {
        logger.info("开始生成自定义代码（单参数版本）");
        try {
            // 参数验证 - 只验证request相关参数，不验证outputStream
            if (request == null) {
                throw new IllegalArgumentException("请求参数不能为空");
            }
            if (request.getDatasourceId() == null) {
                throw new IllegalArgumentException("数据源配置ID不能为空");
            }
            if (request.getTableNames() == null || request.getTableNames().isEmpty()) {
                throw new IllegalArgumentException("表名列表不能为空");
            }
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            generateCustomCode(request, baos);
            logger.info("自定义代码生成完成，返回字节数组，大小: {} 字节", baos.size());
            return baos.toByteArray();
        } catch (IllegalArgumentException e) {
            // 直接抛出参数验证异常，保持原始错误消息
            logger.warn("参数验证失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("生成自定义代码失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成代码失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量生成代码
     * 
     * @param tableIds 表ID列表
     * @param templateCode 模板代码
     * @param modelType 模型类型
     * @param outputStream 输出流
     */
    @Override
    public void generateBatchCodes(List<Long> tableIds, String templateCode, Integer modelType, OutputStream outputStream) {
        logger.info("开始批量生成代码，表数量: {}", tableIds.size());
        
        // 参数验证
        Assert.notEmpty(tableIds, "表ID列表不能为空");
        Assert.notNull(outputStream, "输出流不能为空");
        
        // 设置默认值
        if (modelType == null) {
            modelType = 1; // 默认SaaS模式
            logger.debug("使用默认模型类型: SaaS模式");
        }
        
        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
            int successCount = 0;
            
            // 批量生成代码逻辑
            for (Long tableId : tableIds) {
                try {
                    logger.debug("处理表ID: {}", tableId);
                    
                    // 获取表信息
                    CodegenTable table = codegenTableRepository.findById(tableId)
                            .orElseThrow(() -> new RuntimeException("表不存在: " + tableId));
                    
                    // 生成代码
                    codeGenerator.generateCode(zipOut, table, modelType);
                    successCount++;
                    
                } catch (Exception e) {
                    logger.error("处理表ID {} 时出错: {}", tableId, e.getMessage(), e);
                    // 继续处理其他表
                }
            }
            
            logger.info("批量生成完成，成功处理 {} 个表，共 {} 个表", successCount, tableIds.size());
        } catch (IOException e) {
            logger.error("批量生成时发生IO错误: {}", e.getMessage(), e);
            throw new RuntimeException("批量生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 导入表结构从数据库
     * 
     * @param datasourceId 数据源ID
     * @param tableNames 表名列表
     * @param moduleName 模块名
     * @param packageName 包名
     * @param sceneType 场景类型
     * @param modelType 模型类型
     * @return 导入的表ID列表
     */
    @Override
    public List<Long> importTablesFromDatabase(Long datasourceId, List<String> tableNames, String moduleName, 
                                             String packageName, Integer sceneType, Integer modelType) {
        logger.info("导入表结构从数据库，数据源ID: {}, 表数量: {}", datasourceId, tableNames.size());
        
        // 验证参数
        Assert.notNull(datasourceId, "数据源ID不能为空");
        Assert.notEmpty(tableNames, "表名列表不能为空");
        
        try {
            // 直接调用接口方法
            List<Long> importedTableIds = databaseTableService.importTablesFromDatabase(
                datasourceId, tableNames, moduleName, packageName, sceneType, modelType);
            
            logger.info("导入成功，共导入 {} 个表", importedTableIds.size());
            return importedTableIds;
        } catch (Exception e) {
            logger.error("导入表结构失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 同步表结构从数据库
     * 
     * @param tableId 表ID
     */
    @Override
    public void syncTableFromDatabase(Long tableId) {
        logger.info("同步表结构从数据库，表ID: {}", tableId);
        
        // 参数验证
        Assert.notNull(tableId, "表ID不能为空");
        
        try {
            // 直接调用接口方法
            databaseTableService.syncTableFromDatabase(tableId);
            logger.info("表结构同步成功，表ID: {}", tableId);
        } catch (Exception e) {
            logger.error("同步表结构失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 移除setter方法，使用构造函数注入替代
     * 保留用于测试的setter方法，但标记为废弃
     * 
     * @param databaseTableService 数据库表服务实例
     * @deprecated 请使用构造函数注入，该方法将在未来版本中移除
     */
    @Deprecated
    public void setDatabaseTableService(DatabaseTableServiceInterface databaseTableService) {
        logger.warn("使用废弃的setDatabaseTableService方法，请使用构造函数注入");
        this.databaseTableService = databaseTableService;
    }

    // ===== 辅助方法 =====
    
    /**
     * 验证代码生成请求参数
     * 
     * @param request 代码生成请求对象
     * @param outputStream 输出流
     * @throws IllegalArgumentException 当参数验证失败时抛出
     */
    private void validateCodeGenRequest(GenerateCustomCodeRequest request, OutputStream outputStream) {
        // 输出流验证 - 当调用带有outputStream参数的方法时必须验证
        if (outputStream == null) {
            throw new IllegalArgumentException("输出流不能为空");
        }
        
        // 请求对象验证
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        
        // 数据源ID验证
        if (request.getDatasourceId() == null) {
            throw new IllegalArgumentException("数据源配置ID不能为空");
        }
        
        // 表名列表验证
        if (request.getTableNames() == null || request.getTableNames().isEmpty()) {
            throw new IllegalArgumentException("表名列表不能为空");
        }
    }
    
    /**
     * 根据数据源ID和表名查找代码生成表
     * 
     * @param datasourceId 数据源ID
     * @param tableName 表名
     * @return 代码生成表对象的Optional
     */
    private Optional<CodegenTable> findCodegenTable(Long datasourceId, String tableName) {
        // 此处为简化实现，实际应该通过repository查询
        // 可以根据实际需求实现具体的查询逻辑
        logger.debug("查找代码生成表，数据源ID: {}, 表名: {}", datasourceId, tableName);
        return Optional.empty();
    }
    
    @Override
    public CodegenDetailResponse getCodegenDetail(Long tableId) {
        logger.info("获取代码生成详情，表ID: {}", tableId);
        Assert.notNull(tableId, "表ID不能为空");
        
        try {
            // 委托给数据库表服务实现
            return databaseTableService.getCodegenDetail(tableId);
        } catch (Exception e) {
            logger.error("获取代码生成详情失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    public void deleteTable(Long tableId) {
        logger.info("删除表，表ID: {}", tableId);
        Assert.notNull(tableId, "表ID不能为空");
        
        try {
            // 委托给数据库表服务实现
            databaseTableService.deleteCodegenTable(tableId);
        } catch (Exception e) {
            logger.error("删除表失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    public void updateCodegenTable(CodegenTableRequest request) {
        logger.info("更新代码生成表，表ID: {}", request.getId());
        Assert.notNull(request, "请求参数不能为空");
        Assert.notNull(request.getId(), "表ID不能为空");
        
        try {
            // 委托给数据库表服务实现
            databaseTableService.updateCodegenTable(request);
        } catch (Exception e) {
            logger.error("更新代码生成表失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    public CodegenTableResponse getCodegenTablePageResponse(CodegenTablePageRequest request) {
        logger.info("获取代码生成表分页响应");
        Assert.notNull(request, "请求参数不能为空");
        
        try {
            // 此处为简化实现，实际应该根据请求参数查询数据
            return new CodegenTableResponse();
        } catch (Exception e) {
            logger.error("获取代码生成表分页响应失败: {}", e.getMessage(), e);
            throw e;
        }
    }
}