package ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.controller;

import ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.assembler.${table.customEntityName}Assembler;
import ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.dto.request.Create${table.customEntityName}Req;
import ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.dto.request.Update${table.customEntityName}Req;
import ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.dto.request.${table.customEntityName}PageRequest;
import ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.dto.response.${table.customEntityName}Resp;
import ${utils.getPackagePath(basePackage, moduleName)}.application.${table.customEntityName}ApplicationService;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * ${table.tableComment!'实体'}控制器。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。
 *
 * <p>依赖只有 ApplicationService + Assembler：控制器不注入 {@code domain.repository} / {@code domain.service}，
 * 也不实例化或改领域对象状态（HC 入站边界 / E-6.4）。
 *
 * <p><b>Swagger 写法注意</b>：{@code @ApiResponse} 必须写全限定名——它与信封类 {@code com.bone.core.model.ApiResponse}
 * 同名，短名会撞（blueprint {@code OrderController} 里有同样的注释）。
 *
 * <p><b>授权</b>：本骨架不生成 {@code @PreAuthorize}，因为权限码命名由各模块安全模型决定且需 Spring Security 依赖。
 * 接入鉴权时按 blueprint 写法补 {@code @PreAuthorize("hasAuthority('<ctx>:<resource>:read/write')")}。
 */
@Tag(name = "${table.tableComment!'实体'}管理", description = "提供${table.tableComment!'实体'}相关的 Web 接口")
@RestController
@RequestMapping("${apiPrefix}/${utils.toResourceSegment(table.customEntityName)}")
@RequiredArgsConstructor
public class ${table.customEntityName}Controller {

  private final ${table.customEntityName}ApplicationService applicationService;
  private final ${table.customEntityName}Assembler assembler;

  @Operation(summary = "创建${table.tableComment!'实体'}")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数校验失败"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "${moduleName?upper_case}_${table.customEntityName?upper_case}_NOT_FOUND")
  })
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public ApiResponse<${table.customEntityName}Resp> create(
      @Parameter(description = "创建请求") @Valid @RequestBody Create${table.customEntityName}Req request) {
    Long id = applicationService.create(assembler.toCreate${table.customEntityName}Command(request));
    // 201：信封里的 code 必须与 HTTP 状态一致，故用 success(int, T) 重载而非 success(T)
    return ApiResponse.success(
        HttpStatus.CREATED.value(), assembler.to${table.customEntityName}Resp(applicationService.getById(id)));
  }

  @Operation(summary = "更新${table.tableComment!'实体'}")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "${moduleName?upper_case}_${table.customEntityName?upper_case}_NOT_FOUND"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "${moduleName?upper_case}_${table.customEntityName?upper_case}_CONFLICT: 并发冲突")
  })
  @PutMapping("/{id}")
  public ApiResponse<${table.customEntityName}Resp> update(
      @Parameter(description = "${table.tableComment!'实体'}ID") @PathVariable Long id,
      @Parameter(description = "更新请求") @Valid @RequestBody Update${table.customEntityName}Req request) {
    applicationService.update(assembler.toUpdate${table.customEntityName}Command(id, request));
    return ApiResponse.success(
        assembler.to${table.customEntityName}Resp(applicationService.getById(id)));
  }

  @Operation(summary = "删除${table.tableComment!'实体'}")
  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(
      @Parameter(description = "${table.tableComment!'实体'}ID") @PathVariable Long id) {
    applicationService.delete(id);
    return ApiResponse.success();
  }

  @Operation(summary = "查询${table.tableComment!'实体'}详情")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "${moduleName?upper_case}_${table.customEntityName?upper_case}_NOT_FOUND")
  })
  @GetMapping("/{id}")
  public ApiResponse<${table.customEntityName}Resp> getById(
      @Parameter(description = "${table.tableComment!'实体'}ID") @PathVariable Long id) {
    return ApiResponse.success(assembler.to${table.customEntityName}Resp(applicationService.getById(id)));
  }

  @Operation(summary = "分页查询${table.tableComment!'实体'}")
  @GetMapping
  public ApiResponse<PageResult<${table.customEntityName}Resp>> page(
      @Valid @ModelAttribute ${table.customEntityName}PageRequest qry) {
    return ApiResponse.success(
        applicationService.page(qry.getPage(), qry.getSize()).map(assembler::to${table.customEntityName}Resp));
  }
}
