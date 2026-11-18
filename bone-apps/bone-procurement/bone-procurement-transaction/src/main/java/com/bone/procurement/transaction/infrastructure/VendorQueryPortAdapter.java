package com.bone.procurement.transaction.infrastructure;

import com.bone.procurement.transaction.domain.port.VendorQueryPort;
import java.lang.reflect.Method;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 供应商资格查询适配器（生产）。
 *
 * <p><b>为什么用反射</b>：bone-masterdata 打为 Spring Boot fat jar（类在 BOOT-INF/classes），无法作为普通 classpath
 * 依赖被编译期 import。故通过 Spring 容器按 bean 名取已有的 SupplierApplicationService，反射调用 currentState(String) 并读取
 * DTO 的 vendorState getter。
 *
 * <p><b>fail-closed</b>：bean 缺失、反射失败、查询异常、状态为空一律返回 empty ⇒ 上层建单 31003，绝不默认放行。 法人实体 ID
 * 本期无法从供应商侧解析（DTO 刻意不暴露字段原值），返回 null 由上层降级，已在验证报告如实标注。
 */
@Component
public class VendorQueryPortAdapter implements VendorQueryPort {

  private final ObjectProvider<Object> supplierService;

  public VendorQueryPortAdapter(ObjectProvider<Object> supplierService) {
    this.supplierService = supplierService;
  }

  @Override
  public Optional<VendorSnapshot> findSnapshot(Long tenantId, String partyId) {
    Object service = supplierService.getIfAvailable();
    if (service == null || tenantId == null || partyId == null || partyId.isBlank()) {
      return Optional.empty();
    }
    try {
      Method currentState = service.getClass().getMethod("currentState", String.class);
      Object optional = currentState.invoke(service, partyId.trim());
      if (!(optional instanceof Optional<?> opt) || opt.isEmpty()) {
        return Optional.empty();
      }
      Object dto = opt.get();
      Object vendorState = dto.getClass().getMethod("getVendorState").invoke(dto);
      Object resolvedPartyId = dto.getClass().getMethod("getPartyId").invoke(dto);
      if (vendorState == null) {
        return Optional.empty();
      }
      return Optional.of(
          new VendorSnapshot(
              resolvedPartyId == null ? partyId : resolvedPartyId.toString(),
              vendorState.toString(),
              null));
    } catch (ReflectiveOperationException | RuntimeException e) {
      return Optional.empty();
    }
  }
}
