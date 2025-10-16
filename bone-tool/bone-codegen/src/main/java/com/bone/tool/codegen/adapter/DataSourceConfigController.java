package com.bone.tool.codegen.adapter;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.bone.tool.codegen.application.dto.DataSourceConfigSaveRequest;
import com.bone.tool.codegen.application.dto.TestConnectionRequest;
import com.bone.tool.codegen.application.converter.CodegenConverter;
import com.bone.core.model.ApiResponse;
import com.bone.tool.codegen.application.dto.DataSourceConfigResponse;
import com.bone.tool.codegen.domain.entity.DataSourceConfig;
import com.bone.tool.codegen.domain.service.DataSourceConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import static com.bone.core.model.ApiResponse.success;

/**
 * 数据源配置 控制器
 * <p>
 * 提供数据源配置管理的RESTful API接口，作为领域服务的适配器
 * 支持灵活配置多种类型的数据库数据源
 * 
 * @author bone-team
 */
@Tag(name = "数据源配置管理", description = "提供数据源配置的增删改查、测试连接等功能")
@RestController
@RequestMapping("/api/v1/data-source-configs")
public class DataSourceConfigController {

    @Resource
    private DataSourceConfigService dataSourceConfigService;
    
    @Resource
    private CodegenConverter codegenConverter;

    @PostMapping
    @Operation(summary = "创建数据源配置")
    public ApiResponse<Long> createDataSourceConfig(@RequestBody DataSourceConfigSaveRequest createReqVO) {
        return success(dataSourceConfigService.createDataSourceConfig(createReqVO));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新数据源配置")
    @Parameter(name = "id", description = "数据源配置ID", required = true)
    public ApiResponse<Boolean> updateDataSourceConfig(@PathVariable("id") Long id, @RequestBody DataSourceConfigSaveRequest updateReqVO) {
        // 将id设置到请求对象中，确保更新操作正确关联
        updateReqVO.setId(id);
        dataSourceConfigService.updateDataSourceConfig(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除数据源配置")
    @Parameter(name = "id", description = "数据源配置ID", required = true)
    public ApiResponse<Boolean> deleteDataSourceConfig(@PathVariable("id") Long id) {
        dataSourceConfigService.deleteDataSourceConfig(id);
        return success(true);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取数据源配置详情")
    @Parameter(name = "id", description = "数据源配置ID", required = true, example = "1024")
    public ApiResponse<DataSourceConfigResponse> getDataSourceConfig(@PathVariable("id") Long id) {
        DataSourceConfig config = dataSourceConfigService.getDataSourceConfig(id);
        DataSourceConfigResponse response = codegenConverter.toDataSourceConfigResponse(config);
        // 密码脱敏处理
        if (response.getPassword() != null && !response.getPassword().isEmpty()) {
            response.setPassword("******");
        }
        return success(response);
    }

    @GetMapping
    @Operation(summary = "获取数据源配置列表")
    public ApiResponse<List<DataSourceConfigResponse>> getDataSourceConfigList() {
        // 调用无参的getDataSourceConfigList方法
        List<DataSourceConfig> configList = dataSourceConfigService.getDataSourceConfigList();
        List<DataSourceConfigResponse> responseList = new ArrayList<>(configList.size());
        
        // 转换并处理密码脱敏
        for (DataSourceConfig config : configList) {
            DataSourceConfigResponse response = codegenConverter.toDataSourceConfigResponse(config);
            if (response.getPassword() != null && !response.getPassword().isEmpty()) {
                response.setPassword("******");
            }
            responseList.add(response);
        }
        
        return success(responseList);
    }
    
    @PostMapping("/test-connection")
    @Operation(summary = "测试数据源连接")
    public ApiResponse<Boolean> testConnection(@RequestBody TestConnectionRequest request) {
        DataSourceConfig config = new DataSourceConfig();
        config.setName(request.getName());
        config.setUrl(request.getUrl());
        config.setUsername(request.getUsername());
        config.setPassword(request.getPassword());
        config.setDriverClassName(request.getDriverClassName());
        
        boolean success = dataSourceConfigService.testConnection(config);
        return success(success);
    }
    
    @GetMapping("/db-types")
    @Operation(summary = "获取支持的数据库类型")
    public ApiResponse<List<Map<String, String>>> getSupportedDbTypes() {
        List<Map<String, String>> dbTypes = new ArrayList<>();
        
        // MySQL
        Map<String, String> mysql = new HashMap<>();
        mysql.put("type", "mysql");
        mysql.put("name", "MySQL");
        mysql.put("driver", "com.mysql.cj.jdbc.Driver");
        mysql.put("urlTemplate", "jdbc:mysql://localhost:3306/test_db?useSSL=false&useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai");
        dbTypes.add(mysql);
        
        // PostgreSQL
        Map<String, String> postgresql = new HashMap<>();
        postgresql.put("type", "postgresql");
        postgresql.put("name", "PostgreSQL");
        postgresql.put("driver", "org.postgresql.Driver");
        postgresql.put("urlTemplate", "jdbc:postgresql://localhost:5432/test_db");
        dbTypes.add(postgresql);
        
        // Oracle
        Map<String, String> oracle = new HashMap<>();
        oracle.put("type", "oracle");
        oracle.put("name", "Oracle");
        oracle.put("driver", "oracle.jdbc.OracleDriver");
        oracle.put("urlTemplate", "jdbc:oracle:thin:@localhost:1521:orcl");
        dbTypes.add(oracle);
        
        // SQL Server
        Map<String, String> sqlserver = new HashMap<>();
        sqlserver.put("type", "sqlserver");
        sqlserver.put("name", "SQL Server");
        sqlserver.put("driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver");
        sqlserver.put("urlTemplate", "jdbc:sqlserver://localhost:1433;databaseName=test_db");
        dbTypes.add(sqlserver);
        
        return success(dbTypes);
    }

}
