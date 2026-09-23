package ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import ${utils.getPackagePath(basePackage, moduleName)}.application.${table.customEntityName}ApplicationService;
import ${utils.getPackagePath(basePackage, moduleName)}.domain.model.${utils.toPackageSegment(table.customEntityName)}.${table.customEntityName};
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * ${table.tableComment!'实体'}控制器。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。
 */
@Tag(name = "${table.tableComment!'实体'}", description = "${table.tableComment!'实体'}管理接口")
@RestController
@RequestMapping(PlatformApiPaths.METADATA_V1 + "/${table.originalTableName}")
@RequiredArgsConstructor
public class ${table.customEntityName}Controller {

  private final ${table.customEntityName}ApplicationService applicationService;

  @Operation(summary = "查询 ${table.tableComment!'实体'} 详情")
  @GetMapping("/{id}")
  public ApiResponse<${table.customEntityName}> getById(@PathVariable Long id) {
    return ApiResponse.success(applicationService.get(id));
  }

  @Operation(summary = "分页查询 ${table.tableComment!'实体'}")
  @GetMapping("/page")
  public ApiResponse<PageResult<${table.customEntityName}>> page(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(applicationService.page(page, size));
  }
}
