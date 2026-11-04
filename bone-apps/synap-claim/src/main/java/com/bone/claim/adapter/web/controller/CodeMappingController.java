package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.CodeMappingApplicationService;
import com.bone.claim.application.CodeMappingApplicationService.AliasCommand;
import com.bone.claim.application.CodeMappingApplicationService.AliasView;
import com.bone.claim.application.CodeMappingApplicationService.BatchResolveItem;
import com.bone.claim.application.CodeMappingApplicationService.BatchResolveResultView;
import com.bone.claim.application.CodeMappingApplicationService.BindingCommand;
import com.bone.claim.application.CodeMappingApplicationService.BindingView;
import com.bone.claim.application.CodeMappingApplicationService.CodeItemCommand;
import com.bone.claim.application.CodeMappingApplicationService.CodeItemView;
import com.bone.claim.application.CodeMappingApplicationService.ImportResultView;
import com.bone.claim.application.CodeMappingApplicationService.InsurerCommand;
import com.bone.claim.application.CodeMappingApplicationService.InsurerView;
import com.bone.claim.application.CodeMappingApplicationService.MappingHealthView;
import com.bone.claim.application.CodeMappingApplicationService.MappingView;
import com.bone.claim.application.CodeMappingApplicationService.MissView;
import com.bone.claim.application.CodeMappingApplicationService.PageView;
import com.bone.claim.application.CodeMappingApplicationService.PublishCommand;
import com.bone.claim.application.CodeMappingApplicationService.ResolveCommand;
import com.bone.claim.application.CodeMappingApplicationService.ResolveView;
import com.bone.claim.application.CodeMappingApplicationService.SplitCommand;
import com.bone.claim.application.CodeMappingApplicationService.SplitView;
import com.bone.claim.application.CodeMappingApplicationService.StpGateView;
import com.bone.claim.domain.model.mapping.AliasRelation;
import com.bone.claim.domain.model.mapping.InsurerStatus;
import com.bone.claim.domain.model.mapping.MappingSource;
import com.bone.claim.domain.model.mapping.MappingType;
import com.bone.claim.domain.model.mapping.MatchRule;
import com.bone.core.model.ApiResponse;
import com.bone.core.security.auth.CurrentOperator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 多保司主数据映射接口（{@code /api/v1/claim-config/mappings}）。
 *
 * <p><b>权限口径</b>：映射错了会直接赔错钱，故写操作沿用配置面权限 {@code claim:policy:write}， 读操作 {@code
 * claim:policy:read}——不新造权限码，避免"能配责任却看不了映射"的割裂。
 *
 * <p><b>解析接口为什么也开放给前端</b>：运营在导入映射前需要"试算"——输入保司码看解析成哪个标准码。
 * 试算与收单走<b>同一段逻辑</b>（同一个应用服务方法），否则预览说能解析、收单却解析不出来。 注意：解析接口会写未命中队列，这是有意的——预览期发现的缺口正是最该被修的高频缺口。
 */
@Tag(name = "智能理赔-多保司主数据映射", description = "承保主体 / 保司码表 / 码值映射 / 未命中队列")
@RestController
@RequestMapping("/api/v1/claim-config/mappings")
@RequiredArgsConstructor
public class CodeMappingController {

  private final CodeMappingApplicationService codeMappingApplicationService;

  @Operation(summary = "解析保司码到标准码（未命中会入队）")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @PostMapping("/resolve")
  public ApiResponse<ResolveView> resolve(@RequestBody ResolveReq req) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        codeMappingApplicationService.resolve(
            new ResolveCommand(
                req.insurerCode(),
                req.domain(),
                req.rawCode(),
                req.rawName(),
                req.disambiguationKey(),
                req.caseNo())));
  }

  @Operation(summary = "映射分页（默认当前生效；includeHistory=true 展开历史回放链）")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping
  public ApiResponse<PageView<MappingView>> list(
      @RequestParam(required = false) String insurerCode,
      @RequestParam(required = false) String domain,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "false") boolean includeHistory,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        codeMappingApplicationService.listMappings(
            insurerCode, domain, keyword, includeHistory, page, size));
  }

  @Operation(summary = "批量导入映射（.xlsx，整批校验，内容指纹幂等，上限 2000 行）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping(value = "/import", consumes = "multipart/form-data")
  public ApiResponse<ImportResultView> importMappings(
      @RequestParam String insurerCode,
      @RequestParam String domain,
      @RequestParam("file") org.springframework.web.multipart.MultipartFile file)
      throws java.io.IOException {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        codeMappingApplicationService.importMappings(insurerCode, domain, file.getBytes()));
  }

  @Operation(summary = "下载映射批量导入模板（列头 + 示例行）")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/import/template")
  public org.springframework.http.ResponseEntity<byte[]> importTemplate() {
    byte[] bytes = codeMappingApplicationService.importTemplate();
    return org.springframework.http.ResponseEntity.ok()
        .header(
            org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=mapping-import-template.xlsx")
        .contentType(
            org.springframework.http.MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(bytes);
  }

  @Operation(summary = "主体×域映射健康度（覆盖率/OPEN 未命中/近 7 天新增）")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/health")
  public ApiResponse<MappingHealthView> health(
      @RequestParam String insurerCode, @RequestParam String domain) {
    return ApiResponse.success(
        HttpStatus.OK.value(), codeMappingApplicationService.mappingHealth(insurerCode, domain));
  }

  @Operation(summary = "STP 闸门状态（映射质量→业务风险开关，按保司×域；五把尺任一不达标即阻断自动理算）")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/stp-gate")
  public ApiResponse<StpGateView> stpGate(
      @RequestParam String insurerCode, @RequestParam String domain) {
    return ApiResponse.success(
        HttpStatus.OK.value(), codeMappingApplicationService.stpGate(insurerCode, domain));
  }

  @Operation(summary = "发布映射（同键旧行自动失效）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping
  public ApiResponse<MappingView> publish(@RequestBody PublishReq req) {
    return ApiResponse.success(
        HttpStatus.CREATED.value(),
        codeMappingApplicationService.publish(
            new PublishCommand(
                req.insurerCode(),
                req.domain(),
                req.insurerItemCode(),
                req.disambiguationKey(),
                req.standardCode(),
                parse(MappingType.class, req.mappingType()),
                parse(MatchRule.class, req.matchRule()),
                req.matchScore(),
                req.disambiguationJson(),
                parse(MappingSource.class, req.source()),
                req.versionNo(),
                req.reviewerId())));
  }

  @Operation(summary = "停用映射（保留历史行）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/{id}/disable")
  public ApiResponse<MappingView> disable(
      @PathVariable long id, @RequestParam(required = false) String reasonCode) {
    return ApiResponse.success(
        HttpStatus.OK.value(), codeMappingApplicationService.disable(id, reasonCode));
  }

  @Operation(summary = "未命中队列分页（insurerCode 按保司过滤；sortBy=occurCount 按频次，默认按最近发生）")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/misses")
  public ApiResponse<PageView<MissView>> listMisses(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false) String insurerCode,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        codeMappingApplicationService.listMisses(status, sortBy, insurerCode, page, size));
  }

  @Operation(summary = "处置未命中：建立映射并关闭队列项（独立处置权限，操作人留痕；触发受影响案件重解析）")
  @PreAuthorize("hasAuthority('claim:mapping:dispose')")
  @PostMapping("/misses/{id}/resolve")
  public ApiResponse<MissView> resolveMiss(@PathVariable long id, @RequestBody PublishReq req) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        codeMappingApplicationService.resolveMiss(
            id,
            new PublishCommand(
                req.insurerCode(),
                req.domain(),
                req.insurerItemCode(),
                req.disambiguationKey(),
                req.standardCode(),
                parse(MappingType.class, req.mappingType()),
                parse(MatchRule.class, req.matchRule()),
                req.matchScore(),
                req.disambiguationJson(),
                parse(MappingSource.class, req.source()),
                req.versionNo(),
                req.reviewerId()),
            CurrentOperator.requireId()));
  }

  @Operation(summary = "忽略未命中（运营判定该码无需映射；独立处置权限，操作人留痕）")
  @PreAuthorize("hasAuthority('claim:mapping:dispose')")
  @PostMapping("/misses/{id}/ignore")
  public ApiResponse<MissView> ignoreMiss(@PathVariable long id, @RequestParam String reason) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        codeMappingApplicationService.ignoreMiss(id, reason, CurrentOperator.requireId()));
  }

  @Operation(summary = "批量解析（试算/预检，逐项独立 fail-closed，上限 200 条）")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @PostMapping("/resolve-batch")
  public ApiResponse<BatchResolveResultView> resolveBatch(
      @RequestParam String insurerCode, @RequestBody java.util.List<BatchResolveReq> reqs) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        codeMappingApplicationService.resolveBatch(
            insurerCode,
            reqs.stream()
                .map(
                    r ->
                        new BatchResolveItem(
                            r.domain(), r.rawCode(), r.rawName(), r.disambiguationKey()))
                .toList()));
  }

  public record BatchResolveReq(
      String domain, String rawCode, String rawName, String disambiguationKey) {}

  @Operation(summary = "注册承保主体")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/insurers")
  public ApiResponse<InsurerView> registerInsurer(@RequestBody InsurerReq req) {
    return ApiResponse.success(
        HttpStatus.CREATED.value(),
        codeMappingApplicationService.registerInsurer(
            new InsurerCommand(
                req.insurerCode(),
                req.insurerName(),
                req.insurerShortName(),
                req.insurerType(),
                req.creditCode(),
                req.parentCode())));
  }

  @Operation(summary = "承保主体分页")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/insurers")
  public ApiResponse<PageView<InsurerView>> listInsurers(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(
        HttpStatus.OK.value(), codeMappingApplicationService.listInsurers(keyword, page, size));
  }

  @Operation(summary = "编辑承保主体", description = "编码不可改（它是保单/映射的外键锚点），仅名称/简称/类型/信用代码可编辑")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/insurers/{insurerCode}")
  public ApiResponse<InsurerView> updateInsurer(
      @PathVariable String insurerCode, @RequestBody InsurerReq req) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        codeMappingApplicationService.updateInsurer(
            insurerCode,
            new InsurerCommand(
                insurerCode,
                req.insurerName(),
                req.insurerShortName(),
                req.insurerType(),
                req.creditCode(),
                req.parentCode())));
  }

  @Operation(
      summary = "主体启停/终止合作",
      description = "目标状态 target=ACTIVE/DISABLED/TERMINATED；不删行只改状态，历史单据仍显示该主体")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/insurers/{insurerCode}/status")
  public ApiResponse<InsurerView> changeInsurerStatus(
      @PathVariable String insurerCode, @RequestParam InsurerStatus target) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        codeMappingApplicationService.changeInsurerStatus(insurerCode, target));
  }

  @Operation(summary = "维护保司码表项")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/code-items")
  public ApiResponse<CodeItemView> upsertCodeItem(@RequestBody CodeItemReq req) {
    return ApiResponse.success(
        HttpStatus.CREATED.value(),
        codeMappingApplicationService.upsertCodeItem(
            new CodeItemCommand(
                req.insurerCode(),
                req.domain(),
                req.itemCode(),
                req.itemName(),
                req.extraJson(),
                req.sourceBatchNo())));
  }

  @Operation(summary = "保司码表项分页")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/code-items")
  public ApiResponse<PageView<CodeItemView>> listCodeItems(
      @RequestParam(required = false) String insurerCode,
      @RequestParam(required = false) String domain,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        codeMappingApplicationService.listCodeItems(insurerCode, domain, keyword, page, size));
  }

  @Operation(summary = "登记标准码别名（归并/更名）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/aliases")
  public ApiResponse<AliasView> addAlias(@RequestBody AliasReq req) {
    return ApiResponse.success(
        HttpStatus.CREATED.value(),
        codeMappingApplicationService.addAlias(
            new AliasCommand(
                req.domain(),
                req.aliasCode(),
                req.canonicalCode(),
                parse(AliasRelation.class, req.relation()))));
  }

  @Operation(summary = "登记标准码拆分（1:N，解析时转人工）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/splits")
  public ApiResponse<SplitView> addSplit(@RequestBody SplitReq req) {
    return ApiResponse.success(
        HttpStatus.CREATED.value(),
        codeMappingApplicationService.addSplit(
            new SplitCommand(
                req.domain(), req.parentCode(), req.childCode(), req.splitRuleJson())));
  }

  @Operation(summary = "绑定字段到选项集（insurerCode 空 = 平台标准基线）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/field-bindings")
  public ApiResponse<BindingView> bindField(@RequestBody BindingReq req) {
    return ApiResponse.success(
        HttpStatus.CREATED.value(),
        codeMappingApplicationService.bindField(
            new BindingCommand(
                req.fieldCode(), req.insurerCode(), req.sourceType(), req.refCode())));
  }

  @Operation(summary = "字段-选项集绑定列表（平台基线 '*' + 指定保司专属；字段归一用）")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/field-bindings")
  public ApiResponse<List<BindingView>> listFieldBindings(
      @RequestParam(required = false) String insurerCode) {
    return ApiResponse.success(
        HttpStatus.OK.value(), codeMappingApplicationService.listFieldBindings(insurerCode));
  }

  @Operation(summary = "标准码别名列表（按域；别名是标准码维度，跨保司生效）")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/aliases")
  public ApiResponse<List<AliasView>> listAliases(@RequestParam String domain) {
    return ApiResponse.success(
        HttpStatus.OK.value(), codeMappingApplicationService.listAliases(domain));
  }

  @Operation(summary = "标准码拆分列表（按域；拆分是标准码维度，跨保司生效）")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/splits")
  public ApiResponse<List<SplitView>> listSplits(@RequestParam String domain) {
    return ApiResponse.success(
        HttpStatus.OK.value(), codeMappingApplicationService.listSplits(domain));
  }

  @Operation(summary = "停用标准码别名（保留行，解析不再自动归并）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @DeleteMapping("/aliases/{id}")
  public ApiResponse<AliasView> disableAlias(@PathVariable long id) {
    return ApiResponse.success(
        HttpStatus.OK.value(), codeMappingApplicationService.disableAlias(id));
  }

  @Operation(summary = "停用标准码拆分（保留行，解析不再据此父码转人工拆分）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @DeleteMapping("/splits/{id}")
  public ApiResponse<SplitView> disableSplit(@PathVariable long id) {
    return ApiResponse.success(
        HttpStatus.OK.value(), codeMappingApplicationService.disableSplit(id));
  }

  private static <E extends Enum<E>> E parse(Class<E> type, String value) {
    return value == null || value.isBlank() ? null : Enum.valueOf(type, value.trim());
  }

  // ===================== 请求体 =====================

  public record ResolveReq(
      String insurerCode,
      String domain,
      String rawCode,
      String rawName,
      String disambiguationKey,
      String caseNo) {}

  public record PublishReq(
      String insurerCode,
      String domain,
      String insurerItemCode,
      String disambiguationKey,
      String standardCode,
      String mappingType,
      String matchRule,
      java.math.BigDecimal matchScore,
      String disambiguationJson,
      String source,
      String versionNo,
      Long reviewerId) {}

  public record InsurerReq(
      String insurerCode,
      String insurerName,
      String insurerShortName,
      String insurerType,
      String creditCode,
      String parentCode) {}

  public record CodeItemReq(
      String insurerCode,
      String domain,
      String itemCode,
      String itemName,
      String extraJson,
      String sourceBatchNo) {}

  public record AliasReq(String domain, String aliasCode, String canonicalCode, String relation) {}

  public record SplitReq(
      String domain, String parentCode, String childCode, String splitRuleJson) {}

  public record BindingReq(
      String fieldCode, String insurerCode, String sourceType, String refCode) {}
}
