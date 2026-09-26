package com.bone.system.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.system.adapter.web.assembler.DictAssembler;
import com.bone.system.adapter.web.dto.request.CreateDictItemReq;
import com.bone.system.adapter.web.dto.request.DictItemPageReq;
import com.bone.system.adapter.web.dto.request.MoveDictItemReq;
import com.bone.system.adapter.web.dto.request.UpdateDictItemReq;
import com.bone.system.adapter.web.dto.response.DictItemResp;
import com.bone.system.adapter.web.dto.response.DictItemTextResp;
import com.bone.system.adapter.web.dto.response.DictOptionResp;
import com.bone.system.application.DictApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典项接口（值层），含层级树、多语言译文与全平台复用的下拉数据源 {@code /options}。
 *
 * <p><b>为何读接口不设 {@code @PreAuthorize}</b>：权限目录只登记了 {@code sys:dict:write}，挂一个未登记的 读码会让所有消费方
 * 403；字典是「每个表单都要读」的基础数据，可用性优先（同 {@code ConfigController} 现状，多租户方案 §F2 已记录该项欠账）。
 */
@Tag(name = "字典项", description = "字典值（列表 / 层级树 / 枚举绑定项 / 译文）")
@RestController
@RequestMapping(PlatformApiPaths.SYSTEM_V1 + "/dict/items")
@RequiredArgsConstructor
public class DictItemController {

  private final DictApplicationService dictApplicationService;
  private final DictAssembler dictAssembler;

  @Operation(summary = "新建字典项")
  @PostMapping
  @PreAuthorize("hasAuthority('sys:dict:write')")
  public ApiResponse<Long> create(@Valid @RequestBody CreateDictItemReq req) {
    return ApiResponse.success(dictApplicationService.createItem(dictAssembler.toCommand(req)));
  }

  @Operation(summary = "更新字典项")
  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('sys:dict:write')")
  public ApiResponse<Void> update(
      @PathVariable Long id, @Valid @RequestBody UpdateDictItemReq req) {
    dictApplicationService.updateItem(dictAssembler.toCommand(id, req));
    return ApiResponse.success();
  }

  @Operation(summary = "删除字典项（任一层级视图中仍有子节点时拒绝）")
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('sys:dict:write')")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    dictApplicationService.deleteItem(id);
    return ApiResponse.success();
  }

  @Operation(summary = "移动字典项（换父级 / 调排序，含环与深度校验）")
  @PutMapping("/{id}/move")
  @PreAuthorize("hasAuthority('sys:dict:write')")
  public ApiResponse<Void> move(@PathVariable Long id, @RequestBody MoveDictItemReq req) {
    dictApplicationService.moveItem(dictAssembler.toCommand(id, req));
    return ApiResponse.success();
  }

  @Operation(summary = "设为该值域默认项")
  @PutMapping("/{id}/default")
  @PreAuthorize("hasAuthority('sys:dict:write')")
  public ApiResponse<Void> markDefault(@PathVariable Long id) {
    dictApplicationService.markDefault(id);
    return ApiResponse.success();
  }

  @Operation(summary = "按值域 + 编码读取字典项")
  @GetMapping("/{typeCode}/{code}")
  public ApiResponse<DictItemResp> get(@PathVariable String typeCode, @PathVariable String code) {
    return ApiResponse.success(
        dictApplicationService.getItem(typeCode, code).map(dictAssembler::toResp).orElse(null));
  }

  @Operation(summary = "字典项分页（扁平视图）")
  @GetMapping("/page")
  public ApiResponse<PageResult<DictItemResp>> page(DictItemPageReq req) {
    return ApiResponse.success(
        dictApplicationService.pageItems(dictAssembler.toQuery(req)).map(dictAssembler::toResp));
  }

  @Operation(summary = "指定层级视图的树（hierarchyCode 缺省 DEFAULT）")
  @GetMapping("/tree")
  public ApiResponse<List<DictItemResp>> tree(
      @RequestParam String typeCode, @RequestParam(required = false) String hierarchyCode) {
    return ApiResponse.success(
        dictApplicationService.treeItems(typeCode, hierarchyCode).stream()
            .map(dictAssembler::toResp)
            .toList());
  }

  @Operation(summary = "多语言译文列表")
  @GetMapping("/{typeCode}/{code}/texts")
  public ApiResponse<List<DictItemTextResp>> texts(
      @PathVariable String typeCode, @PathVariable String code) {
    return ApiResponse.success(
        dictApplicationService.listTexts(typeCode, code).stream()
            .map(dictAssembler::toResp)
            .toList());
  }

  @Operation(summary = "批量保存多语言译文（按 language upsert）")
  @PutMapping("/{typeCode}/{code}/texts")
  @PreAuthorize("hasAuthority('sys:dict:write')")
  public ApiResponse<Integer> saveTexts(
      @PathVariable String typeCode,
      @PathVariable String code,
      @RequestBody List<DictItemTextResp> payload) {
    return ApiResponse.success(
        dictApplicationService.saveTexts(
            typeCode, code, payload.stream().map(dictAssembler::toDto).toList()));
  }

  /**
   * 下拉数据源：平台 + 租户覆盖合并 → 生效过滤 → 只返启用项 → 可选本地化，走进程内缓存。
   *
   * <p>这是全平台消费字典的唯一入口——前端 {@code useDict} 与后端其它模块都只认它， 避免 v1「每个页面自己拼字典请求」的重复实现。
   */
  @Operation(summary = "下拉数据源（合并平台与租户覆盖，只含启用且生效中的项）")
  @GetMapping("/options")
  public ApiResponse<List<DictOptionResp>> options(
      @RequestParam String type,
      @RequestParam(required = false) String parent,
      @RequestParam(required = false) String lang) {
    return ApiResponse.success(
        dictApplicationService.options(type, parent, lang).stream()
            .map(dictAssembler::toResp)
            .toList());
  }
}
