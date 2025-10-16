package com.bone.tool.codegen.adapter;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.tool.codegen.application.dto.CodegenTablePageRequest;
import com.bone.tool.codegen.application.dto.CodegenTableResponse;
import com.bone.tool.codegen.application.dto.CodegenCreateListRequest;
import com.bone.tool.codegen.application.dto.CodegenTableRequest;
import com.bone.tool.codegen.application.dto.CodegenDetailResponse;
import com.bone.tool.codegen.application.dto.GenerateCustomCodeRequest;
import com.bone.tool.codegen.domain.entity.TableInfo;
import com.bone.tool.codegen.domain.service.CodegenService;
import com.bone.tool.codegen.domain.service.DataSourceConfigService;
import com.bone.tool.codegen.domain.service.DatabaseTableService;
import com.bone.tool.codegen.domain.enums.ModelTypeEnum;
import com.bone.tool.codegen.domain.entity.DataSourceConfig;
import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.tool.codegen.domain.entity.CodegenColumn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.bone.core.model.ApiResponse.success;

/**
 * 代码生成器 控制器
 * <p>
 * 提供代码生成相关的RESTful API接口，作为领域服务的适配器
 * 
 * @author bone-team
 */
@Tag(name = "代码生成器管理")
@RestController
@RequestMapping("/api/v1/codegen")
@Validated
public class CodegenController {

    @Resource
    private CodegenService codegenService;
    
    @Resource
    private DataSourceConfigService dataSourceConfigService;
    
    @Resource
    private DatabaseTableService databaseTableService;

    @Operation(summary = "获得数据库的表和字段")
    @GetMapping("/database-table/list")
    public ApiResponse<List<TableInfo>> getDatabaseTableList(@RequestParam("dataSourceConfigId") Long dataSourceConfigId) {
        List<TableInfo> tables = databaseTableService.getTableList(dataSourceConfigId);
        return success(tables);
    }

    @GetMapping("/table/list")
    @Operation(summary = "获得表定义列表")
    @Parameter(name = "dataSourceConfigId", description = "数据源配置的编号", required = true, example = "1")
    public ApiResponse<List<CodegenTableResponse>> getCodegenTableList(@RequestParam(value = "dataSourceConfigId") Long dataSourceConfigId) {
        // 从数据库获取表定义列表
        List<com.bone.tool.codegen.domain.entity.CodegenTable> codegenTables = codegenService.getCodegenTablesByDataSourceId(dataSourceConfigId);
        
        // 将实体对象转换为响应对象
        List<CodegenTableResponse> responseList = new ArrayList<>(codegenTables.size());
        for (com.bone.tool.codegen.domain.entity.CodegenTable table : codegenTables) {
            // 使用BeanUtil进行对象转换
            CodegenTableResponse response = cn.hutool.core.bean.BeanUtil.toBean(table, CodegenTableResponse.class);
            
            // 设置额外的数据源名称信息
            try {
                DataSourceConfig dataSourceConfig = dataSourceConfigService.getDataSourceConfig(dataSourceConfigId);
                if (dataSourceConfig != null) {
                    response.setDataSourceConfigName(dataSourceConfig.getName());
                }
            } catch (Exception e) {
                // 忽略获取数据源名称失败的异常
            }
            
            responseList.add(response);
        }
        
        return success(responseList);
    }

    @GetMapping("/table/page")
    @Operation(summary = "获得表定义的分页")
    public ApiResponse<PageResult<CodegenTableResponse>> getCodegenTablePage(@Valid CodegenTablePageRequest reqVO) {
        // 获取分页数据
        PageResult<CodegenTable> pageResult = codegenService.getCodegenTablePage(reqVO);
        
        // 将实体对象转换为响应对象
        List<CodegenTableResponse> responseList = pageResult.getRecords().stream()
                .map(table -> {
                    CodegenTableResponse response = new CodegenTableResponse();
                    try {
                        cn.hutool.core.bean.BeanUtil.copyProperties(table, response);
                        // 设置数据源名称
                        DataSourceConfig dataSourceConfig = dataSourceConfigService.getDataSourceConfig(table.getDataSourceConfigId());
                        response.setDataSourceConfigName(dataSourceConfig.getName());
                    } catch (Exception e) {
                        // 忽略获取数据源名称失败的异常
                    }
                    return response;
                })
                .collect(java.util.stream.Collectors.toList());
        
        // 构建分页响应
        PageResult<CodegenTableResponse> responsePage = PageResult.of(responseList, pageResult.getTotal(), pageResult.getPage(), pageResult.getSize());
        
        return success(responsePage);
    }

    @Operation(summary = "获得表和字段的明细")
    @GetMapping("/detail")
    @Parameter(name = "tableId", description = "表编号", required = true, example = "1024")
    public ApiResponse<CodegenDetailResponse> getCodegenDetail(@RequestParam("tableId") Long tableId) {
        // 通过服务层获取表配置和字段列表
        CodegenDetailResponse response = codegenService.getCodegenDetail(tableId);
        return success(response);
    }

    @Operation(summary = "基于数据库的表结构，创建代码生成器的表和字段定义")
    @PostMapping("/create-list")
    public ApiResponse<List<Long>> createCodegenList(@Valid @RequestBody CodegenCreateListRequest reqVO) {
        // 使用默认值导入表结构
        List<Long> tableIds = codegenService.importTablesFromDB(
                reqVO.getDataSourceConfigId(),
                reqVO.getTableNames(),
                "default", // 默认模块名
                "com.bone", // 默认包名
                1, // 默认场景类型
                1); // 默认模型类型
        return success(tableIds);
    }

    @Operation(summary = "更新代码")
    @PutMapping("/update")
    public ApiResponse<Boolean> updateCodegen(@Valid @RequestBody CodegenTableRequest reqVO) {
        codegenService.updateCodegenTable(reqVO);
        return success(true);
    }

    @Operation(summary = "基于数据库的表结构，同步数据库的表和字段定义")
    @PutMapping("/sync-from-db")
    @Parameter(name = "tableId", description = "表编号", required = true, example = "1024")
    public ApiResponse<Boolean> syncCodegenFromDB(@RequestParam("tableId") Long tableId) {
        codegenService.syncCodegenFromDB(tableId);
        return success(true);
    }

    @Operation(summary = "删除数据库的表和字段定义")
    @DeleteMapping("/delete")
    @Parameter(name = "tableId", description = "表编号", required = true, example = "1024")
    public ApiResponse<Boolean> deleteCodegen(@RequestParam("tableId") Long tableId) {
        codegenService.deleteTable(tableId);
        return success(true);
    }

    @Operation(summary = "下载代码")
    @GetMapping("/download")
    @Parameter(name = "tableId", description = "表编号", required = true, example = "1024")
    public void downloadCodegen(@RequestParam("tableId") Long tableId, HttpServletResponse response) throws IOException {
        // 生成代码并打包下载
        codegenService.generateBatchCode(
                Collections.singletonList(tableId),
                "default", // 使用默认分组
                ModelTypeEnum.SAAS.getType(), // 使用SaaS模式
                response.getOutputStream());
        
        // 设置响应头
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=codegen.zip");
    }

    @Operation(summary = "下载生成代码（多张表）")
    @GetMapping("/download2")
    @Parameter(name = "tableId", description = "表编号", required = true, example = "1024")
    public void downloadCodegen(@RequestParam("tableId") List<Long> tableIdList,
                                HttpServletResponse response) throws IOException {
        // 简化实现，不做实际操作
        response.setStatus(200);
    }
    
    @PostMapping("/generate/custom")
    @Operation(summary = "增强版批量生成代码")
    public void generateCustomCode(HttpServletResponse response, @RequestBody @Valid GenerateCustomCodeRequest request) throws Exception {
        // 调用服务层方法生成代码
        byte[] zipBytes = codegenService.generateCustomCode(request);
        
        // 设置响应头
        String fileName = "codegen-" + request.getProjectName() + ".zip";
        response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        response.setContentType("application/zip");
        response.setContentLength(zipBytes.length);
        
        // 写入响应
        try (OutputStream out = response.getOutputStream()) {
            out.write(zipBytes);
            out.flush();
        }
    }
    
    @GetMapping("/db-types")
    @Operation(summary = "获取支持的数据库类型")
    public ApiResponse<List<DataSourceConfig>> getSupportedDbTypes() {
        // 从数据库读取所有数据源配置
        List<DataSourceConfig> dataSourceConfigs = dataSourceConfigService.getAllDataSourceConfigs();
        
        // 对返回的数据源配置列表进行密码脱敏处理
        for (DataSourceConfig config : dataSourceConfigs) {
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                config.setPassword("******");
            }
        }
        
        return success(dataSourceConfigs);
    }
}
