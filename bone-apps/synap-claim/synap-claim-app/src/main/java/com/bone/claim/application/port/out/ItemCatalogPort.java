package com.bone.claim.application.port.out;

import com.bone.claim.domain.model.adjustment.engine.CatalogAdmission;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 目录准入端口（缺口 ① 费用明细筛选 / 适用限定「药品·诊疗」维度的唯一取数入口）。
 *
 * <p><b>为什么目录由端口取、而不是本仓库建一张目录表</b>：目录是<b>外部主数据</b>——它由医保局/再保/接入方维护，
 * 有独立版本节奏与生效期。在本库放一份副本，立刻变成"两个真源"：谁新谁旧说不清，出事时无法回答"当时按哪一版判的"。 故本仓库只定义<b>查询契约</b>与<b>fail-closed
 * 行为</b>，目录数据由接入方提供。
 *
 * <p><b>为什么必须带 {@code businessDate}</b>：目录有生效期。用当前版本算去年的发票是这类系统最常见的系统性错误——
 * 它不会报错，只会安静地多赔或少赔，而且每一笔都"看起来合理"。业务日取<b>发票日期</b>（缺失回落赔案受理日）， 与日限额 Q-05 同一条口径。
 *
 * <p><b>三态是硬要求，不是可选项</b>：实现方必须区分"目录内 / 目录外 / 判不出来"。把"判不出来"折进任何一侧都会出事——
 * 折进目录内则目录形同虚设（配了照赔，超赔且不可见）；折进目录外则替客户下拒赔决定，而真实原因可能只是编码没录。 判不出来时返回 {@link
 * com.bone.claim.domain.model.adjustment.engine.CatalogAdmission.Verdict#UNKNOWN}， 引擎会以 {@code
 * CATALOG_UNRESOLVED} 退补件。
 *
 * <p><b>缺失行为由 {@code ItemCatalogGateway} 兜底</b>：本端口<b>没有</b>默认放行实现。未接入时网关返回"全部 {@code UNKNOWN}"并打
 * WARN，配了 {@code MANDATORY} 的责任因此<b>算不出金额</b>而不是全额赔付—— 这类"配了不生效"的缺口正是本机制要消灭的。
 *
 * @see com.bone.claim.domain.model.adjustment.engine.CatalogAdmission
 */
public interface ItemCatalogPort {

  /**
   * 查询一批费用明细在指定目录、指定基准日的准入结论。
   *
   * @param catalogCode 目录编码（责任配置指定，如 {@code NATIONAL_2024}）
   * @param businessDate 判定基准日（发票日期，缺失回落赔案受理日）
   * @param items 待判定的明细（{@code itemCode} 可能为空——实现方须对空编码返回 {@code UNKNOWN}，不得自行补默值）
   * @return {@code itemId → 结论}；<b>未覆盖的明细可以不出现在返回里</b>（引擎按 {@code UNKNOWN} 处理）， 但不得为图省事返回空 map
   *     之外的全量 {@code COVERED}
   */
  Map<Long, CatalogAdmission.Verdict> check(
      String catalogCode, LocalDate businessDate, List<Item> items);

  /**
   * 待判定的明细。
   *
   * @param itemId 费用明细 ID（返回结果的键，必须原样带回）
   * @param itemCode 费用编码（目录主数据的键；可能为空）
   */
  record Item(Long itemId, String itemCode) {}
}
