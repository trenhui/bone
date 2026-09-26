package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.application.MasterDataCategoryApplicationService;
import com.bone.masterdata.application.command.AssignRecordCategoryCommand;
import com.bone.masterdata.application.command.CreateMasterDataCategoryCommand;
import com.bone.masterdata.application.command.UpdateMasterDataCategoryCommand;
import com.bone.masterdata.domain.model.category.MasterDataCategory;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 主数据分类控制器（G4）：树维护 + 记录归类。 */
@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/categories")
@RequiredArgsConstructor
public class MasterDataCategoryController {

  private final MasterDataCategoryApplicationService categoryService;

  @PreAuthorize("hasAuthority('masterdata:categories:write')")
  @PostMapping
  public ApiResponse<Long> create(@Valid @RequestBody CreateMasterDataCategoryCommand cmd) {
    return ApiResponse.success(categoryService.create(cmd));
  }

  @PreAuthorize("hasAuthority('masterdata:categories:write')")
  @PutMapping("/{id}")
  public ApiResponse<Void> update(
      @PathVariable Long id, @Valid @RequestBody UpdateMasterDataCategoryCommand cmd) {
    cmd.setId(id);
    categoryService.update(cmd);
    return ApiResponse.success();
  }

  @PreAuthorize("hasAuthority('masterdata:categories:write')")
  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    categoryService.delete(id);
    return ApiResponse.success();
  }

  @GetMapping
  public ApiResponse<List<MasterDataCategory>> tree(@RequestParam Long masterDataEntityId) {
    return ApiResponse.success(categoryService.tree(masterDataEntityId));
  }

  /** 记录归类：POST /categories/assign，body 为 {recordId, categoryId}。 */
  @PreAuthorize("hasAuthority('masterdata:categories:write')")
  @PostMapping("/assign")
  public ApiResponse<Void> assignRecord(@Valid @RequestBody AssignRecordCategoryCommand cmd) {
    categoryService.assignRecord(cmd);
    return ApiResponse.success();
  }

  @PreAuthorize("hasAuthority('masterdata:categories:write')")
  @DeleteMapping("/assign")
  public ApiResponse<Void> unassignRecord(
      @RequestParam Long recordId, @RequestParam Long categoryId) {
    categoryService.unassignRecord(recordId, categoryId);
    return ApiResponse.success();
  }

  @GetMapping("/record/{recordId}")
  public ApiResponse<List<Long>> recordCategories(@PathVariable Long recordId) {
    return ApiResponse.success(categoryService.recordCategoryIds(recordId));
  }
}
