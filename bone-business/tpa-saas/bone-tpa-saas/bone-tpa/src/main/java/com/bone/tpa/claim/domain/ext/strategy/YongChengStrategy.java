package com.bone.tpa.claim.domain.ext.strategy;

import com.bone.tpa.claim.application.request.QueryListRequest;
import com.bone.tpa.claim.domain.service.ClaimInvoiceService;
import com.bone.tpa.claim.domain.service.ClaimStakeholderService;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.enums.PersonTypeEnum;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.*;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;


/**
 * 赔案创建检验的默认实现，
 */
@Service
public class YongChengStrategy extends DefaultStrategy {
    @Autowired
    private ClaimInvoiceService claimInvoiceService;

    @Autowired
    private ClaimStakeholderService claimStakeholderService;


    public void validate(Claim claim) throws ValidationException {
        if (claim == null) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "Claim cannot be null.");
        }
        if (claim.getClaimNo() == null) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "ClaimNo cannot be null.");
        }

        //我稍微偷个懒，这里不判断保司了
        QueryListRequest queryListRequest = new QueryListRequest();
        queryListRequest.setId(String.valueOf(claim.getId()));
        queryListRequest.setTenantId(String.valueOf(claim.getTenantId()));
        queryListRequest.setBizIdentityCode(claim.getBizIdentityCode());
        List<ClaimInvoice> claimInvoiceList = claimInvoiceService.getClaimInvoices(queryListRequest);

        //
        BigDecimal totalInvoiceTotalAmount = claimInvoiceList.stream().map(ClaimInvoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInvoiceTotalMedicalFundPayment = claimInvoiceList.stream().map(ClaimInvoice::getTotalMedicalFundPayment).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal subtract = totalInvoiceTotalAmount.subtract(totalInvoiceTotalMedicalFundPayment);

        //若差值大于10000，则出险人身份证有效期必须填写
        if (subtract.compareTo(BigDecimal.valueOf(10000)) >= 0) {
            ClaimStakeholder outInsure = claimStakeholderService.getByClaimId(claim.getId(), claim.getBizIdentityCode(),
                    claim.getTenantId(), Collections.singletonList(PersonTypeEnum.OUT_INSURE.getCode())).get(0);
            if (outInsure.getIdentityDatePeriod() == null) {
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "出险人证件有效期必填。");
            }
        }


        /**
        //检测发票金额和项目金额是否合理
        GetListRequest getInvoiceListRequest = new GetListRequest();
        getInvoiceListRequest.setBizIdentityCode(claim.getBizIdentityCode());
        getInvoiceListRequest.setTenantId(claim.getTenantId());
        getInvoiceListRequest.setId(claim.getId());

        List<ClaimInvoice> invoiceList = claimInvoiceService.getClaimInvoices(getInvoiceListRequest);

        for (ClaimInvoice invoice : invoiceList) {
            GetListRequest getListRequest = new GetListRequest();
            getListRequest.setBizIdentityCode(invoice.getBizIdentityCode());
            getListRequest.setTenantId(invoice.getTenantId());
            getListRequest.setId(invoice.getId());

            List<InvoiceProject> projectList = invoiceProjectService.getInvoiceProjects(getListRequest);
            List<InvoiceProjectItem> itemList = invoiceProjectItemService.getInvoiceProjectItems(getListRequest);

            Map<String, BigDecimal> itemTotalMap = itemList.stream().collect(Collectors.toMap(InvoiceProjectItem::getProjectName, x -> x.getTotalAmount() == null ? BigDecimal.ZERO : x.getTotalAmount()));
            BigDecimal projectTotal = BigDecimal.valueOf(0);

            for (InvoiceProject project : projectList) {
                projectTotal = projectTotal.add(project.getInvoiceAmount());
                if (itemTotalMap.containsKey(project.getProjectName())) {
                    //如果项目的金额要小于明细的金额，证明是有问题的
                    if (project.getInvoiceAmount().compareTo(itemTotalMap.get(project.getProjectName())) < 0) {
                        throw new BizException(BizErrorCode.ITEM_MONEY_TOO_MUCH, project.getProjectName());
                    }
                }
            }

            //如果发票的金额要小于项目的金额，也证明是有问题的
            if (invoice.getTotalAmount().compareTo(projectTotal) < 0) {
                throw new BizException(BizErrorCode.PROJECT_MONEY_TOO_MUCH, invoice.getInvoiceNo());
            }
        }*/

    }

}
