package com.bone.tpa.audit.application.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.bone.tpa.audit.application.InvoiceApplicationService;
import com.bone.tpa.audit.application.request.BatchUpdateAmountDTO;
import com.bone.tpa.audit.application.request.BatchUpdateDutyDTO;
import com.bone.tpa.audit.application.request.CopyInvoiceDTO;
import com.bone.tpa.audit.domain.service.InvoiceReviewService;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InvoiceApplicationServiceImpl implements InvoiceApplicationService {

    @Autowired
    private InvoiceReviewService invoiceReviewService;

    @Override
    public void batchDutyUpdate(BatchUpdateDutyDTO batchUpdateDutyDTO) {
        if (batchUpdateDutyDTO.getClaimId() == null) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "赔案号不能为空");
        }
        if (CollectionUtil.isEmpty(batchUpdateDutyDTO.getDutyIds())) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "请选择需批量操作的责任");
        }
        if (CollectionUtil.isEmpty(batchUpdateDutyDTO.getInvoiceIds())) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "请选择需批量操作的发票");
        }
        if (StringUtils.isBlank(batchUpdateDutyDTO.getPolicyNo())) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "请先选择保单再设置责任");
        }
        invoiceReviewService.updateInvoiceDuty(batchUpdateDutyDTO);
    }


    @Override
    public void batchUpdateAmount(BatchUpdateAmountDTO batchUpdateAmountDTO) {
        if (batchUpdateAmountDTO.getClaimId() == null) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "赔案号不能为空");
        }
        if (CollectionUtil.isEmpty(batchUpdateAmountDTO.getInvoiceIds())) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "请选择需批量操作的发票");
        }
        invoiceReviewService.updateInvoiceAmount(batchUpdateAmountDTO);
    }

    @Override
    public void copy(CopyInvoiceDTO copyInvoiceDTO) {
        if (copyInvoiceDTO.getClaimId() == null) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "赔案号不能为空");
        }
        if (copyInvoiceDTO.getInvoiceId() == null) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "请选择发票");
        }
        if (StringUtils.isBlank(copyInvoiceDTO.getUpdateInvoiceNo())) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "请输入新发票号码");
        }
        invoiceReviewService.copyInvoice(copyInvoiceDTO);
    }
}
