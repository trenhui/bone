package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.FactorMatrixApplicationService;
import com.bone.claim.application.FactorMatrixApplicationService.CellInput;
import com.bone.claim.application.FactorMatrixApplicationService.FormulaView;
import com.bone.claim.application.FactorMatrixApplicationService.MatrixView;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.util.List;
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

/** 理算责任因子矩阵配置（版本化、结构变更保留已填值、发布冻结）。 */
@Tag(name = "ClaimFactorMatrix", description = "理算责任因子矩阵配置")
@RestController
@RequestMapping("/api/v1/claim-config/factor-matrix")
@RequiredArgsConstructor
public class FactorMatrixController {

  private final FactorMatrixApplicationService factorMatrixApplicationService;

  @Operation(summary = "加载矩阵（按责任 + 版本；dimensionSet 为空时取全部因子组合）")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/{liabilityId}")
  public ApiResponse<MatrixView> load(
      @PathVariable long liabilityId,
      @RequestParam(defaultValue = "1") int version,
      @RequestParam(required = false) String dimensionSet) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        factorMatrixApplicationService.load(liabilityId, dimensionSet, version));
  }

  @Operation(summary = "保存矩阵（按 rowKey/colKey merge，保留已填值；已发布版本拒绝）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PutMapping("/{liabilityId}")
  public ApiResponse<MatrixView> save(
      @PathVariable long liabilityId, @RequestBody SaveMatrixReq req) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        factorMatrixApplicationService.save(
            liabilityId, req.dimensionSet(), req.version(), req.cells()));
  }

  @Operation(summary = "发布矩阵（整版冻结，之后不可改）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/{liabilityId}/publish")
  public ApiResponse<Integer> publish(
      @PathVariable long liabilityId,
      @RequestParam(defaultValue = "1") int version,
      @RequestParam(required = false) String dimensionSet) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        factorMatrixApplicationService.publish(liabilityId, dimensionSet, version));
  }

  @Operation(summary = "受控 token 拼装公式（括号配对失败/非法因子标识 → 400；不落库）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/generate-formula")
  public ApiResponse<FormulaView> generateFormula(@RequestBody GenerateFormulaReq req) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        factorMatrixApplicationService.generateFormula(req.factors(), req.cells()));
  }

  /** 矩阵保存请求；单元格 value 为格值（比例/免赔/限额）。 */
  public record SaveMatrixReq(
      String dimensionSet, @NotNull Integer version, List<CellInput> cells) {}

  /** 公式生成请求；factors 为受控因子标识，cells 为格值样本。 */
  public record GenerateFormulaReq(List<String> factors, List<CellInput> cells) {}
}
