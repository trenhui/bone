package com.bone.tpa.audit.application;

import com.bone.tpa.audit.application.request.BatchUpdateAmountDTO;
import com.bone.tpa.audit.application.request.BatchUpdateDutyDTO;
import com.bone.tpa.audit.application.request.CopyInvoiceDTO;

public interface InvoiceApplicationService {
    void batchDutyUpdate(BatchUpdateDutyDTO batchUpdateDutyDTO);
    void batchUpdateAmount(BatchUpdateAmountDTO batchUpdateAmountDTO);
    void copy(CopyInvoiceDTO copyInvoiceDTO);
}
