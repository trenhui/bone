package com.bone.claim.application.catalog;

import com.bone.claim.application.port.out.ItemCatalogPort;
import com.bone.claim.domain.model.adjustment.engine.CatalogAdmission;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 目录准入网关：把「端口在不在」这件事挡住，让应用层只看到一个恒定可用的查询入口。
 *
 * <p><b>为什么不给端口一个"默认放行"的 Spring 实现</b>：那会让"忘了接线"表现为"全额赔付"——错得安静，
 * 且与"目录里确实全都在"在数据上完全无法区分。这里反过来：<b>没接线 ⇒ 全部 {@code UNKNOWN} ⇒ 配了 {@code MANDATORY}
 * 的责任算不出金额并退补件</b>。错得响，第一次跑就能发现。
 *
 * <p><b>为什么用 {@link ObjectProvider} 而不是 {@code @ConditionalOnMissingBean}</b>：本模块是普通 Spring Boot
 * 应用（非 auto-configuration），{@code @ConditionalOnMissingBean} 在组件扫描顺序上不可靠—— 接入方若以自己的
 * {@code @Component} 实现端口，可能与默认实现同时注册，报 duplicate bean 而起不来。 用 {@code ObjectProvider}
 * 在<b>调用时</b>解析，没有注册顺序问题，也让这个兜底在单测里可以直接构造。
 *
 * <p>未接线时只打<b>一次</b> WARN（每次理算都打会把日志淹没，而这件事是部署期事实，不是每案事件）。
 */
@Component
public class ItemCatalogGateway {

  private static final Logger log = LoggerFactory.getLogger(ItemCatalogGateway.class);

  private final ObjectProvider<ItemCatalogPort> ports;

  /** 解析结果缓存：端口在运行期不会变，避免每次都问一遍容器。 */
  private volatile ItemCatalogPort resolved;

  private volatile boolean resolutionDone;

  private volatile boolean warned;

  /**
   * 容器装配入口。
   *
   * <p>显式 {@code @Autowired}：本类另有一个私有构造用于单测，两个构造时容器无法自行择一， 不标注会在启动期报 "no default
   * constructor"，而这是部署期才暴露的错误。
   */
  @Autowired
  public ItemCatalogGateway(ObjectProvider<ItemCatalogPort> ports) {
    this.ports = ports;
  }

  private ItemCatalogGateway(ItemCatalogPort port) {
    this.ports = null;
    this.resolved = port;
    this.resolutionDone = true;
  }

  /** 未接线形态（单测 / 未接入目录的部署）。 */
  public static ItemCatalogGateway unwired() {
    return new ItemCatalogGateway((ItemCatalogPort) null);
  }

  /** 已接线形态（单测注入固定实现）。 */
  public static ItemCatalogGateway of(ItemCatalogPort port) {
    return new ItemCatalogGateway(port);
  }

  /**
   * 查询一批明细的准入结论。
   *
   * @return {@code itemId → 结论}；端口未接线时返回空 map（引擎按 {@code UNKNOWN} 逐条 fail-closed）
   */
  public Map<Long, CatalogAdmission.Verdict> check(
      String catalogCode, LocalDate businessDate, List<ItemCatalogPort.Item> items) {
    ItemCatalogPort port = port();
    if (port == null) {
      return Map.of();
    }
    Map<Long, CatalogAdmission.Verdict> verdicts = port.check(catalogCode, businessDate, items);
    return verdicts == null ? Map.of() : verdicts;
  }

  /** 端口是否已接入（供诊断与自检使用，不改变任何金额语义）。 */
  public boolean wired() {
    return port() != null;
  }

  private ItemCatalogPort port() {
    if (!resolutionDone) {
      synchronized (this) {
        if (!resolutionDone) {
          resolved = ports == null ? null : ports.getIfAvailable();
          resolutionDone = true;
        }
      }
    }
    if (resolved == null && !warned) {
      warned = true;
      log.warn(
          "ItemCatalogPort 未接入：目录准入（缺口 ① / 适用限定药品·诊疗）对配了 MANDATORY 的责任将一律"
              + "退回补件（CATALOG_UNRESOLVED），不会退化成全额赔付。接入方需以 @Component 提供该端口实现");
    }
    return resolved;
  }
}
