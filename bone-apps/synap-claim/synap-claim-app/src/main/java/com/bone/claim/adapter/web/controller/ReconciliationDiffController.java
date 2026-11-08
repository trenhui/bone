package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.ReconciliationDispositionService;
import com.bone.claim.application.ReconciliationDispositionService.Disposition;
import com.bone.claim.domain.model.reconciliation.ReconciliationDiff;
import com.bone.claim.domain.model.reconciliation.projection.PendingDiff;
import com.bone.core.model.ApiResponse;
import com.bone.core.security.auth.CurrentOperator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对账差异处置接口（运维面，§11.7 "检出 → 派单 → 处置 → 留痕"的最后两环）。
 *
 * <p><b>为什么处置是 POST 而不是改差异行的 PUT</b>：处置<b>不改</b>差异本身（金额、阶段、类型都是证据，改它就是改证据）， 只追加一条处置结论。PUT
 * 语义会让人以为差异行可以被编辑。
 *
 * <p><b>操作人</b>取自已验签 token（{@link CurrentOperator#requireId()}）：处置流水要能追到人，不接受请求体自报。
 *
 * <p><b>权限</b>用两个既有的资金写码放行，不新造 {@code claim:recon:write}——新权限码不落库就是"菜单可见、端点全 403"（0074
 * 记过这个坑），而处置本就是资金岗的动作。
 */
@Tag(name = "智能理赔-对账处置", description = "对账差异的待处置队列、处置结论与处置历史")
@RestController
@RequestMapping("/api/v1/claim-ops/reconciliation-diffs")
@RequiredArgsConstructor
public class ReconciliationDiffController {

  private final ReconciliationDispositionService dispositionService;

  /** 处置请求。<b>没有目标金额字段</b>：处置只表态，不动钱（动钱走补偿流水）。 */
  public record ResolveReq(
      @NotBlank(message = "处置结论必填（RESOLVED/ADJUSTED/WAIVED）") String resolution, String note) {}

  @Operation(
      summary = "待处置差异队列",
      description =
          "没有处置流水的差异，按发现时间倒序。注意不是按 resolution='PENDING' 查——" + "处置态不落差异行，PENDING 列不参与筛选（见服务类注释）。")
  @PreAuthorize("hasAnyAuthority('claim:quota:write','claim:settlement:write')")
  @GetMapping("/pending")
  public ApiResponse<List<PendingDiff>> pending(
      @Parameter(description = "条数上限（1..500）") @RequestParam(defaultValue = "50") int limit) {
    return ApiResponse.success(HttpStatus.OK.value(), dispositionService.pending(limit));
  }

  @Operation(
      summary = "处置一条差异",
      description =
          "PENDING → RESOLVED/ADJUSTED/WAIVED，单向一次。已处置或结论非法返回 409（不静默幂等："
              + "三种结论对应三种后续动作，覆盖会让调用方误以为自己的结论生效）。")
  @PreAuthorize("hasAnyAuthority('claim:quota:write','claim:settlement:write')")
  @PostMapping("/{diffId}/resolution")
  public ApiResponse<Disposition> resolve(
      @Parameter(description = "差异ID") @PathVariable long diffId,
      @Valid @RequestBody ResolveReq request) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        dispositionService.resolveDiff(
            diffId, request.resolution(), request.note(), CurrentOperator.requireId()));
  }

  @Operation(summary = "某条差异的处置历史", description = "按发生顺序；正常一条，多次说明被更正过")
  @PreAuthorize("hasAnyAuthority('claim:quota:write','claim:settlement:write')")
  @GetMapping("/{diffId}/actions")
  public ApiResponse<List<Disposition>> history(
      @Parameter(description = "差异ID") @PathVariable long diffId) {
    return ApiResponse.success(HttpStatus.OK.value(), dispositionService.history(diffId));
  }

  /** 合法的处置结论取值（供前端渲染下拉；不要在前端再抄一份）。 */
  @Operation(summary = "处置结论取值范围")
  @PreAuthorize("hasAnyAuthority('claim:quota:write','claim:settlement:write')")
  @GetMapping("/resolutions")
  public ApiResponse<List<String>> resolutions() {
    return ApiResponse.success(HttpStatus.OK.value(), List.copyOf(ReconciliationDiff.RESOLUTIONS));
  }
}
