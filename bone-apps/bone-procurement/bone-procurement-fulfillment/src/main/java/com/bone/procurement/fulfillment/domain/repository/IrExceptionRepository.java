package com.bone.procurement.fulfillment.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.fulfillment.domain.model.exception.IrException;
import java.util.List;
import java.util.Optional;

/** IR 例外仓储。去重键 (tenant_id, invoice_no, exception_code)。 */
public interface IrExceptionRepository extends Repository<IrException, Long> {

  default Optional<IrException> findByDedupKey(
      Long tenantId, String invoiceNo, String exceptionCode) {
    List<IrException> list =
        findByCriteria(
            Criteria.<IrException>create()
                .entityClass(IrException.class)
                .eq("tenantId", tenantId)
                .eq("invoiceNo", invoiceNo)
                .eq("exceptionCode", exceptionCode));
    return list.stream().findFirst();
  }

  default Optional<IrException> findByExceptionNo(Long tenantId, String exceptionNo) {
    List<IrException> list =
        findByCriteria(
            Criteria.<IrException>create()
                .entityClass(IrException.class)
                .eq("tenantId", tenantId)
                .eq("exceptionNo", exceptionNo));
    return list.stream().findFirst();
  }

  /**
   * 例外分页（只读）：按租户锚定。例外表按 LLD §4 不含 vendor_party_id，供应商维度隔离在 application 层 经 invoice_no →
   * 发票归属校验执行；仓储只保证租户隔离。
   */
  default PageResult<IrException> findPage(Long tenantId, int page, int size) {
    return pageByCriteria(
        Criteria.<IrException>create()
            .entityClass(IrException.class)
            .eq("tenantId", tenantId)
            .orderByDesc("createdAt")
            .page(page, size));
  }
}
