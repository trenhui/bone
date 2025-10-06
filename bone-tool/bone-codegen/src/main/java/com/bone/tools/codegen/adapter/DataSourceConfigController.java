package com.bone.tools.codegen.adapter;


import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageParam;
import com.bone.core.model.PageResult;
import com.bone.tools.codegen.util.BeanUtils;
import com.bone.tools.codegen.application.dto.DataSourceConfigResponse;
import com.bone.tools.codegen.application.dto.DataSourceConfigSaveRequest;
import com.bone.tools.codegen.domain.entity.DataSourceConfigDO;
import com.bone.tools.codegen.domain.service.DataSourceConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;

import static com.bone.core.model.ApiResponse.success;

@Tag(name = "管理后台 - 数据源配置")
@RestController
@RequestMapping("/data-source-config")
public class DataSourceConfigController {

    @Resource
    private DataSourceConfigService dataSourceConfigService;

    @PostMapping("/create")
    @Operation(summary = "创建数据源配置")
    public ApiResponse<Long> createDataSourceConfig(@RequestBody DataSourceConfigSaveRequest createReqVO) {
        return success(dataSourceConfigService.createDataSourceConfig(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新数据源配置")
    public ApiResponse<Boolean> updateDataSourceConfig(@RequestBody DataSourceConfigSaveRequest updateReqVO) {
        dataSourceConfigService.updateDataSourceConfig(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除数据源配置")
    @Parameter(name = "id", description = "编号", required = true)
    public ApiResponse<Boolean> deleteDataSourceConfig(@RequestParam("id") Long id) {
        dataSourceConfigService.deleteDataSourceConfig(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得数据源配置")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    public ApiResponse<DataSourceConfigResponse> getDataSourceConfig(@RequestParam("id") Long id) {
        DataSourceConfigDO config = dataSourceConfigService.getDataSourceConfig(id);
        return success(BeanUtils.toBean(config, DataSourceConfigResponse.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获得数据源配置列表")
    public ApiResponse<PageResult<DataSourceConfigResponse>> getDataSourceConfigList(PageParam pageParam) {
        PageResult<DataSourceConfigResponse> result = BeanUtils.toBean(dataSourceConfigService.getDataSourceConfigList(pageParam), DataSourceConfigResponse.class);
        result.getRecords().forEach(x -> x.setCreateTimeStr(x.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        return success(result);
    }

}
