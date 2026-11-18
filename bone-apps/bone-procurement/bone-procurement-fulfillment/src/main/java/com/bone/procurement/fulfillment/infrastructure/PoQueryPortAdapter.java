package com.bone.procurement.fulfillment.infrastructure;

import com.bone.procurement.fulfillment.domain.port.PoQueryPort;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * PO 只读查询适配器（生产）。
 *
 * <p>通过 ApplicationContext 按 bean 名精确取 transaction 的只读 API（application service + 行仓储），反射调用， 不
 * import transaction 具体类型⇒ fulfillment domain 不反向依赖上游上下文。任一环节缺失/异常一律返回 empty（fail-closed）， 不臆造 PO
 * 状态或行。
 */
@Component
public class PoQueryPortAdapter implements PoQueryPort {

  private static final String PO_SERVICE_BEAN = "purchaseOrderApplicationService";
  private static final String LINE_REPO_BEAN = "purchaseOrderLineRepository";

  private final ApplicationContext context;

  public PoQueryPortAdapter(ApplicationContext context) {
    this.context = context;
  }

  @Override
  public Optional<PoSnapshot> findByNo(Long tenantId, String poNo) {
    if (tenantId == null || poNo == null || poNo.isBlank()) {
      return Optional.empty();
    }
    try {
      Object poService = context.getBean(PO_SERVICE_BEAN);
      Object lineRepo = context.getBean(LINE_REPO_BEAN);
      Method findByNo = poService.getClass().getMethod("findByNo", Long.class, String.class);
      Object opt = findByNo.invoke(poService, tenantId, poNo);
      if (!(opt instanceof Optional<?> o) || o.isEmpty()) {
        return Optional.empty();
      }
      Object po = o.get();
      String status = (String) invokeGetter(po, "getStatus");
      Object versionObj = invokeGetter(po, "getVersion");
      Object poId = invokeGetter(po, "getId");
      String vendor = (String) invokeGetter(po, "getVendorPartyId");

      Method findLines = lineRepo.getClass().getMethod("findByPoId", Long.class, Long.class);
      Object linesObj = findLines.invoke(lineRepo, tenantId, poId);
      List<PoSnapshot.Line> lines = new ArrayList<>();
      if (linesObj instanceof List<?> list) {
        for (Object line : list) {
          lines.add(
              new PoSnapshot.Line(
                  ((Number) invokeGetter(line, "getLineNo")).intValue(),
                  str(invokeGetter(line, "getItemId")),
                  dec(invokeGetter(line, "getQuantity")),
                  dec(invokeGetter(line, "getNetPrice")),
                  dec(invokeGetter(line, "getLineAmount"))));
        }
      }
      return Optional.of(
          new PoSnapshot(
              poNo,
              versionObj == null ? 0 : ((Number) versionObj).intValue(),
              status,
              vendor,
              List.copyOf(lines)));
    } catch (ReflectiveOperationException | RuntimeException e) {
      return Optional.empty();
    }
  }

  private static Object invokeGetter(Object target, String name)
      throws ReflectiveOperationException {
    return target.getClass().getMethod(name).invoke(target);
  }

  private static String str(Object v) {
    return v == null ? null : v.toString();
  }

  private static BigDecimal dec(Object v) {
    return v instanceof BigDecimal b ? b : null;
  }
}
