package com.bone.masterdata.testsupport.supplier;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.gateway.VendorRepositoryPort;
import com.bone.masterdata.domain.model.supplier.Vendor;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 内存版 Vendor 出站端口：仅测试与演示可见；生产 adapter 属 R-2。同样实现乐观版本校验。 */
public final class InMemoryVendorRepositoryPort implements VendorRepositoryPort {

  private final Map<String, Vendor> byKey = new ConcurrentHashMap<>();
  private int saveCount;

  @Override
  public Optional<Vendor> findCurrent(long tenantId, String partyId) {
    return Optional.ofNullable(byKey.get(key(tenantId, partyId)));
  }

  @Override
  public void save(Vendor vendor, long expectedVersion) {
    String k = key(vendor.tenantId(), vendor.partyId());
    Vendor current = byKey.get(k);
    long actual = current == null ? 0L : current.version();
    if (actual != expectedVersion) {
      throw new DomainException(
          "并发版本冲突：partyId=" + vendor.partyId() + " 期望 " + expectedVersion + " 实际 " + actual);
    }
    byKey.put(k, vendor);
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

  private static String key(long tenantId, String partyId) {
    return tenantId + "|" + partyId;
  }
}
