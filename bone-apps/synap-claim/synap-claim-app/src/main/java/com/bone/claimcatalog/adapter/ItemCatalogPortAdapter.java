package com.bone.claimcatalog.adapter;

import com.bone.claim.application.port.out.CatalogDataPort;
import com.bone.claim.application.port.out.CatalogDataPort.CatalogSnapshot;
import com.bone.claim.application.port.out.ItemCatalogPort;
import com.bone.claim.domain.model.adjustment.engine.CatalogAdmission.Verdict;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 目录准入端口的 fail-closed 生产实现（可插拔接入方适配器，默认不激活）。
 *
 * <p><b>为何不在 com.bone.claim 包树内</b>：就绪度复核器以源码字面 {@code implements ItemCatalogPort}
 * 判定"端口已接线"。本适配器默认不装配（{@code bone.claim.catalog.mode} 缺省非 external）， 若放在扫描树内会让判据在目录策略改 MANDATORY
 * 后误判为绿，而运行期端口其实没装配 ⇒ 全量 CATALOG_UNRESOLVED 停摆——正是"配了不生效"假绿。放在 claim 包树外，让"代码就绪"与
 * "判据可判的已接线事实"解耦：真正启用目录准入时，由接入方把本包加入组件扫描并配 mode=external。
 *
 * <p>仅在 {@code bone.claim.catalog.mode=external} 时装配；未开启时本 Bean 不存在， {@link
 * com.bone.claim.application.catalog.ItemCatalogGateway} 仍返回空 map（逐条 UNKNOWN），行为与今日逐字一致。
 *
 * <p><b>为什么逐条 UNKNOWN 且 map 非空</b>：{@code CatalogAdmission.of} 在 MANDATORY 且 verdicts 为空时抛 {@code
 * CATALOG_UNRESOLVED} 全局异常（连哪条明细判不出来都说不清）。本适配器对每条输入都给出明确结论， 让引擎能逐条归因退补件；"判不出来"永不折叠成
 * COVERED（目录形同虚设）或 EXCLUDED（替客户拒赔）。
 *
 * <p><b>取数异常一律吞掉转 UNKNOWN</b>：目录服务超时 / 无生效版本不能变成全额赔付，也不能让理算抛异常中断整案。
 */
@Component
@ConditionalOnProperty(
    prefix = "bone.claim.catalog",
    name = "mode",
    havingValue = "external",
    matchIfMissing = false)
public class ItemCatalogPortAdapter implements ItemCatalogPort {

  private static final Logger log = LoggerFactory.getLogger(ItemCatalogPortAdapter.class);

  private final ObjectProvider<CatalogDataPort> dataPorts;

  public ItemCatalogPortAdapter(ObjectProvider<CatalogDataPort> dataPorts) {
    this.dataPorts = dataPorts;
  }

  @Override
  public Map<Long, Verdict> check(String catalogCode, LocalDate businessDate, List<Item> items) {
    Map<Long, Verdict> verdicts = new LinkedHashMap<>();
    if (items == null || items.isEmpty()) {
      return verdicts;
    }
    CatalogDataPort dataPort = dataPorts == null ? null : dataPorts.getIfAvailable();
    Optional<CatalogSnapshot> snapshot = Optional.empty();
    if (dataPort == null) {
      log.warn("CatalogDataPort 未接入：catalogCode={} 全部明细返回 UNKNOWN（fail-closed）", catalogCode);
    } else {
      try {
        List<String> codes = new ArrayList<>();
        for (Item it : items) {
          if (it != null && it.itemCode() != null && !it.itemCode().isBlank()) {
            codes.add(it.itemCode());
          }
        }
        snapshot = dataPort.lookup(catalogCode, businessDate, codes);
      } catch (Exception ex) {
        log.warn(
            "目录取数失败：catalogCode={} businessDate={} 全部明细返回 UNKNOWN", catalogCode, businessDate, ex);
      }
    }

    CatalogSnapshot snap = snapshot.orElse(null);
    int covered = 0;
    int excluded = 0;
    int unknown = 0;
    for (Item it : items) {
      Verdict verdict = verdictOf(it, snap);
      verdicts.put(it == null ? null : it.itemId(), verdict);
      if (verdict == Verdict.COVERED) {
        covered++;
      } else if (verdict == Verdict.EXCLUDED) {
        excluded++;
      } else {
        unknown++;
      }
    }
    log.info(
        "目录准入结算：catalogCode={} businessDate={} total={} COVERED={} EXCLUDED={} UNKNOWN={}",
        catalogCode,
        businessDate,
        items.size(),
        covered,
        excluded,
        unknown);
    return verdicts;
  }

  private Verdict verdictOf(Item item, CatalogSnapshot snapshot) {
    if (item == null || item.itemId() == null) {
      return Verdict.UNKNOWN;
    }
    if (item.itemCode() == null || item.itemCode().isBlank()) {
      return Verdict.UNKNOWN;
    }
    if (snapshot == null) {
      return Verdict.UNKNOWN;
    }
    Verdict verdict = snapshot.byItemCode().get(item.itemCode());
    return Objects.requireNonNullElse(verdict, Verdict.UNKNOWN);
  }
}
