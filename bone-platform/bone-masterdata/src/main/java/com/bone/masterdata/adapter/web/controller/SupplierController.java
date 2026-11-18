package com.bone.masterdata.adapter.web.controller;

import com.bone.core.exception.DomainException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.application.SupplierApplicationService;
import com.bone.masterdata.application.command.RegisterSupplierCommand;
import com.bone.masterdata.application.command.TransitionSupplierCommand;
import com.bone.masterdata.application.command.TransitionVendorCommand;
import com.bone.masterdata.application.query.dto.SupplierRegistrationResultDTO;
import com.bone.masterdata.application.query.dto.SupplierStateDTO;
import com.bone.masterdata.application.query.dto.SupplierTransitionResultDTO;
import com.bone.masterdata.application.query.qry.SupplierPageQuery;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采购供应商主数据治理 API（Supplier 合作资格 / Vendor 交易付款资格）。
 *
 * <p><b>响应契约</b>：一律 {@code ApiResponse<T>} 包装，列表用 {@code PageResult<T>}；不裸返领域对象，
 * 也不回传任何字段原值或身份主键（统一社会信用代码 / 税号 / 银行账号）。
 *
 * <p><b>权限契约</b>：写操作用 {@code masterdata:governance:write}，读操作用 {@code masterdata:governance:read}；
 * 与 Golden Record 治理端点同一套权限点，避免「同一批数据管家要两套授权」。
 *
 * <p><b>租户契约</b>：端点不接受 {@code tenantId} 路径或查询参数；命令体里的 tenantId 只用于与登录态比对， 不一致由应用层
 * fail-closed。列表查询的租户完全取自 JWT。
 *
 * <p><b>能力开关</b>：与 {@code SupplierApplicationService} 同受 {@code
 * bone.procurement.supplier-mdm.enabled=true} 控制。生产持久化 adapter 与跨域守卫属 R-2 未交付，因此默认关闭时端点不注册 ——
 * 不暴露「能调用但落不了库」的假能力。
 */
@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/suppliers")
@ConditionalOnProperty(
    prefix = "bone.procurement.supplier-mdm",
    name = "enabled",
    havingValue = "true")
public class SupplierController {

  private final SupplierApplicationService suppliers;

  public SupplierController(SupplierApplicationService suppliers) {
    this.suppliers = suppliers;
  }

  /** 用例一：合并装配登记（身份解析 → 内核合并 → 裁决成立才落库建角色）。 */
  @PreAuthorize("hasAuthority('masterdata:governance:write')")
  @PostMapping("/registrations")
  public ApiResponse<SupplierRegistrationResultDTO> register(
      @Valid @RequestBody RegisterSupplierCommand cmd) {
    return ApiResponse.success(suppliers.register(cmd));
  }

  /** 用例二：推进 Supplier 合作资格状态（退出经跨域守卫裁决）。 */
  @PreAuthorize("hasAuthority('masterdata:governance:write')")
  @PostMapping("/{partyId}/supplier-transitions")
  public ApiResponse<SupplierTransitionResultDTO> transitionSupplier(
      @PathVariable String partyId, @Valid @RequestBody TransitionSupplierCommand cmd) {
    cmd.setPartyId(resolvePartyId(partyId, cmd.getPartyId()));
    return ApiResponse.success(suppliers.transitionSupplier(cmd));
  }

  /** 用例三：推进 Vendor 交易付款资格状态（解除付款冻结只接受 EXTERNAL_FINANCE 来源）。 */
  @PreAuthorize("hasAuthority('masterdata:governance:write')")
  @PostMapping("/{partyId}/vendor-transitions")
  public ApiResponse<SupplierTransitionResultDTO> transitionVendor(
      @PathVariable String partyId, @Valid @RequestBody TransitionVendorCommand cmd) {
    cmd.setPartyId(resolvePartyId(partyId, cmd.getPartyId()));
    return ApiResponse.success(suppliers.transitionVendor(cmd));
  }

  /** 用例四：只读当前双角色状态与迁移摘要。 */
  @PreAuthorize("hasAuthority('masterdata:governance:read')")
  @GetMapping("/{partyId}")
  public ApiResponse<SupplierStateDTO> current(@PathVariable String partyId) {
    return ApiResponse.success(suppliers.currentState(partyId).orElse(null));
  }

  /** 用例五：按登录租户分页列举合作资格。 */
  @PreAuthorize("hasAuthority('masterdata:governance:read')")
  @GetMapping
  public ApiResponse<PageResult<SupplierStateDTO>> page(SupplierPageQuery qry) {
    return ApiResponse.success(suppliers.pageSuppliers(qry));
  }

  /**
   * 路径 partyId 为唯一真源：命令体省略时直接采用路径值，命令体另填且与路径不一致则拒绝。
   *
   * <p>不做「路径优先、静默覆盖命令体」：那会让客户端以为迁移动的是命令体里的主体，实际动的是路径里的主体 —— 在供应商主数据里这等于错并主体。
   */
  private static String resolvePartyId(String pathPartyId, String bodyPartyId) {
    if (bodyPartyId == null || bodyPartyId.trim().isEmpty()) {
      return pathPartyId;
    }
    if (!bodyPartyId.trim().equals(pathPartyId)) {
      throw new DomainException("路径 partyId 与命令体 partyId 不一致，禁止错并主体");
    }
    return pathPartyId;
  }
}
