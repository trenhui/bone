package com.bone.tpa.claim.application;

import com.bone.tpa.claim.application.request.InvoiceUpdateRequest;
import com.bone.tpa.claim.domain.service.ClaimInvoiceService;
import com.bone.tpa.claim.infrastructure.log.SimpleLog;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 发票应用层服务
 *
 * 目前暂不使用
 */
@Service
public class ClaimInvoiceApplicationService {
    @Autowired
    private ClaimInvoiceService claimInvoiceService;

    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public List<Long> updateInvoiceField(InvoiceUpdateRequest request) {
        List<ClaimInvoice> invoiceList = claimInvoiceService.updateInvoiceField(request);

        return invoiceList.stream().map(ClaimInvoice::getId).toList();
    }

}
