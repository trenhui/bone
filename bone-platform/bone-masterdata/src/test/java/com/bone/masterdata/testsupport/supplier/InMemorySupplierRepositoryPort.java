package com.bone.masterdata.testsupport.supplier;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.gateway.SupplierRepositoryPort;
import com.bone.masterdata.domain.model.supplier.Supplier;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版 Supplier 出站端口：<b>仅测试与演示可见</b>，生产制品里没有本 bean。
 *
 * <p>这不是「过渡实现」—— 端口契约与领域语义在终局图中原位存在，本类只替换持久化机制。生产 adapter 与 {@code t_proc_*} 表属 R-2，须架构审批后另行 change
 * 落地。
 *
 * <p><b>实现乐观版本校验</b>：{@code expectedVersion} 与当前版本不符时抛冲突，与生产 adapter 的语义一致；
 * 若夹具不校验，任何并发覆盖类缺陷在测试里都不会暴露（独立审查 I-02）。
 */
public final class InMemorySupplierRepositoryPort implements SupplierRepositoryPort {

  private final Map<String, Supplier> byKey = new ConcurrentHashMap<>();

  @Override
  public Optional<Supplier> findCurrent(long tenantId, String partyId) {
    return Optional.ofNullable(byKey.get(key(tenantId, partyId)));
  }

  @Override
  public List<Supplier> listByTenant(long tenantId, int offset, int limit) {
    return byKey.values().stream()
        .filter(supplier -> supplier.tenantId() == tenantId)
        .sorted(Comparator.comparing(Supplier::partyId))
        .skip(Math.max(offset, 0))
        .limit(Math.max(limit, 0))
        .toList();
  }

  @Override
  public long countByTenant(long tenantId) {
    return byKey.values().stream().filter(supplier -> supplier.tenantId() == tenantId).count();
  }

  @Override
  public void save(Supplier supplier, long expectedVersion) {
    String k = key(supplier.tenantId(), supplier.partyId());
    Supplier current = byKey.get(k);
    long actual = current == null ? 0L : current.version();
    if (actual != expectedVersion) {
      throw new DomainException(
          "并发版本冲突：partyId=" + supplier.partyId() + " 期望 " + expectedVersion + " 实际 " + actual);
    }
    byKey.put(k, supplier);
    saveCount++;
  }

  /** 供负向探针核对「零副作用」：记录累计写入次数。 */
  public int saveCount() {
    return saveCount;
  }

  /** 隔离用例：清空落库内容与计数，避免用例间靠执行顺序互相污染。 */
  public void reset() {
    byKey.clear();
    saveCount = 0;
  }

  private int saveCount;

  private static String key(long tenantId, String partyId) {
    return tenantId + "|" + partyId;
  }
}
