package com.bone.metadata.catalog.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.metadata.catalog.application.EnumSourceApplicationService;
import com.bone.metadata.catalog.application.EnumSourceApplicationService.DictBindingStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典绑定可观测指标端点（界面设计器字典打通 v1.1 §8）：绑定覆盖率 / 降级率 / 漂移待刷新数。
 *
 * <p><b>为什么是显式端点而不只靠 Prometheus</b>：漂移数要按 distinct typeCode 查真源版本，做成 Gauge 会让每次抓取都压一轮字典服务； 显式端点让「验收
 * / 巡检 / 探针」按需取数，高频趋势交给 {@code bone.dict.resolve} 计数器。
 *
 * <p>读端点不挂 {@code @PreAuthorize}（同 {@code MetaFieldOptionsController} 读端点姿态）：返回的是聚合计数，
 * 不含字典内容与租户敏感数据。
 */
@RestController
@RequestMapping("/api/v1/metadata/dict-binding/metrics")
@RequiredArgsConstructor
@Tag(name = "字典绑定指标", description = "绑定覆盖率 / 降级率 / 漂移待刷新数")
public class DictBindingMetricsController {

  private final EnumSourceApplicationService enumSourceApplicationService;

  @GetMapping
  @Operation(
      summary = "三个可观测指标快照",
      description = "按需计算（按 typeCode 去重查真源版本）；unresolvedBindings 单列，不混入 driftPending")
  public ApiResponse<DictBindingStats> stats() {
    return ApiResponse.success(enumSourceApplicationService.dictBindingStats());
  }
}
