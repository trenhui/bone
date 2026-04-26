package com.bone.tpa.claim.flow.stage;

import com.bone.core.util.PkListUtil;
import com.bone.tpa.claim.domain.service.ClaimInvoiceService;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.domain.service.InvoiceImageRelationService;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.claim.model.InvoiceImageRelation;
import com.bone.tpa.sdk.dao.ClaimRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BaseStageService {
    @Autowired
    protected ClaimInvoiceService invoiceService;

    @Autowired
    protected InvoiceImageRelationService invoiceImageRelationService;

    @Autowired
    protected ClaimRepository claimRepository;

    @Autowired
    protected ClaimService claimService;

    /**
     * 检验是否关联影像件
     * @param claimNumber
     * @return
     */
    protected Map<String,String> checkInvoiceBindImage(Long claimNumber){
        Map<String,String> rs= new HashMap<>();
        List<ClaimInvoice> invoiceList =  invoiceService.getClaimInvoicesByClaimNumber(claimNumber);
        for(ClaimInvoice invoice : invoiceList){
            String invoiceUuid =invoice.getInvoiceUuid();
            List<InvoiceImageRelation>  bindInfo =  invoiceImageRelationService.getByInvoiceUuid(PkListUtil.asList(invoiceUuid));
            if(PkListUtil.isEmpty(bindInfo)){
                rs.put("invoiceImageBind","发票是否关联影响件校验失败");
                break;
            }
        }
        return  rs;
    }

    protected String errorMapToString(Map<String,String>  errorMap){
        StringBuffer stringBuffer = new StringBuffer();
        errorMap.entrySet().stream().forEach(t->{
            stringBuffer.append(t.getValue());
            stringBuffer.append(" \r\n");
        });
        return stringBuffer.toString();
    }

    /**
     * 校验发票录入类型
     * @param claimNumber
     * @return
     */
    protected  Map<String,String> checkInvoiceDeepType(Long claimNumber){
        Map<String,String> rs= new HashMap<>();

        //TODO

        return rs;
    }
}
