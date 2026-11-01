package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.ReferenceLibraryApplicationService;
import com.bone.claim.application.ReferenceLibraryApplicationService.DiseaseCommand;
import com.bone.claim.application.ReferenceLibraryApplicationService.DiseaseView;
import com.bone.claim.application.ReferenceLibraryApplicationService.DrugCommand;
import com.bone.claim.application.ReferenceLibraryApplicationService.DrugView;
import com.bone.claim.application.ReferenceLibraryApplicationService.FeeCategoryCommand;
import com.bone.claim.application.ReferenceLibraryApplicationService.FeeCategoryView;
import com.bone.claim.application.ReferenceLibraryApplicationService.HospitalCommand;
import com.bone.claim.application.ReferenceLibraryApplicationService.HospitalView;
import com.bone.claim.application.ReferenceLibraryApplicationService.Page;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基础目录库接口（0094）。
 *
 * <p><b>命名空间</b>：{@code /api/v1/claim-config/libraries/*}，挂在保单配置命名空间下。 目录库是责任配置（适用限定名单 /
 * 费用规则）的编码来源， 权限码复用 {@code claim:policy:read/write}—— 能配置责任的人才能维护目录，不再另设权限码。
 */
@Tag(name = "智能理赔-基础目录库", description = "医院/疾病/药品/费用类别四库的租户级维护")
@RestController
@RequestMapping("/api/v1/claim-config/libraries")
@RequiredArgsConstructor
public class ReferenceLibraryController {

  private final ReferenceLibraryApplicationService referenceLibraryApplicationService;

  // ===================== 医院 =====================

  @Operation(summary = "医院分页列表", description = "编码/名称模糊搜索")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/hospitals")
  public ApiResponse<Page<HospitalView>> listHospitals(
      @Parameter(description = "关键字(编码/名称)") @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        referenceLibraryApplicationService.listHospitals(keyword, page, size));
  }

  @Operation(summary = "新建医院")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/hospitals")
  public ApiResponse<HospitalView> createHospital(@Valid @RequestBody HospitalCommand request) {
    return ApiResponse.success(
        HttpStatus.OK.value(), referenceLibraryApplicationService.createHospital(request));
  }

  @Operation(summary = "编辑医院", description = "编码不可改（主数据键）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PutMapping("/hospitals/{id}")
  public ApiResponse<HospitalView> updateHospital(
      @Parameter(description = "医院ID") @PathVariable long id,
      @Valid @RequestBody HospitalCommand request) {
    return ApiResponse.success(
        HttpStatus.OK.value(), referenceLibraryApplicationService.updateHospital(id, request));
  }

  // ===================== 疾病 =====================

  @Operation(summary = "疾病分页列表", description = "编码/名称模糊搜索")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/diseases")
  public ApiResponse<Page<DiseaseView>> listDiseases(
      @Parameter(description = "关键字(编码/名称)") @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        referenceLibraryApplicationService.listDiseases(keyword, page, size));
  }

  @Operation(summary = "新建疾病")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/diseases")
  public ApiResponse<DiseaseView> createDisease(@Valid @RequestBody DiseaseCommand request) {
    return ApiResponse.success(
        HttpStatus.OK.value(), referenceLibraryApplicationService.createDisease(request));
  }

  @Operation(summary = "编辑疾病", description = "编码不可改（主数据键）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PutMapping("/diseases/{id}")
  public ApiResponse<DiseaseView> updateDisease(
      @Parameter(description = "疾病ID") @PathVariable long id,
      @Valid @RequestBody DiseaseCommand request) {
    return ApiResponse.success(
        HttpStatus.OK.value(), referenceLibraryApplicationService.updateDisease(id, request));
  }

  // ===================== 药品 =====================

  @Operation(summary = "药品分页列表", description = "编码/名称模糊搜索")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/drugs")
  public ApiResponse<Page<DrugView>> listDrugs(
      @Parameter(description = "关键字(编码/名称)") @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(
        HttpStatus.OK.value(), referenceLibraryApplicationService.listDrugs(keyword, page, size));
  }

  @Operation(summary = "新建药品")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/drugs")
  public ApiResponse<DrugView> createDrug(@Valid @RequestBody DrugCommand request) {
    return ApiResponse.success(
        HttpStatus.OK.value(), referenceLibraryApplicationService.createDrug(request));
  }

  @Operation(summary = "编辑药品", description = "编码不可改（主数据键）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PutMapping("/drugs/{id}")
  public ApiResponse<DrugView> updateDrug(
      @Parameter(description = "药品ID") @PathVariable long id,
      @Valid @RequestBody DrugCommand request) {
    return ApiResponse.success(
        HttpStatus.OK.value(), referenceLibraryApplicationService.updateDrug(id, request));
  }

  // ===================== 费用类别 =====================

  @Operation(summary = "费用类别分页列表")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/fee-categories")
  public ApiResponse<Page<FeeCategoryView>> listFeeCategories(
      @Parameter(description = "关键字(编码/名称)") @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        referenceLibraryApplicationService.listFeeCategories(keyword, page, size));
  }

  @Operation(summary = "费用类别全量下拉", description = "费用规则编辑用，按 sort_no 排序，不分页")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/fee-categories/all")
  public ApiResponse<java.util.List<FeeCategoryView>> listAllFeeCategories() {
    return ApiResponse.success(
        HttpStatus.OK.value(), referenceLibraryApplicationService.listAllFeeCategories());
  }

  @Operation(summary = "新建费用类别")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/fee-categories")
  public ApiResponse<FeeCategoryView> createFeeCategory(
      @Valid @RequestBody FeeCategoryCommand request) {
    return ApiResponse.success(
        HttpStatus.OK.value(), referenceLibraryApplicationService.createFeeCategory(request));
  }

  @Operation(summary = "编辑费用类别", description = "编码不可改（主数据键，费用明细归集口径）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PutMapping("/fee-categories/{id}")
  public ApiResponse<FeeCategoryView> updateFeeCategory(
      @Parameter(description = "费用类别ID") @PathVariable long id,
      @Valid @RequestBody FeeCategoryCommand request) {
    return ApiResponse.success(
        HttpStatus.OK.value(), referenceLibraryApplicationService.updateFeeCategory(id, request));
  }
}
