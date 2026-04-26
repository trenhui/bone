package com.bone.tpa.soa.syncevent;

import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.dao.ClaimStakeholderRepository;
import com.bone.tpa.sdk.dao.InvoiceProjectItemRepository;
import com.bone.tpa.sdk.dao.InvoiceProjectRepository;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.claim.sync.ClaimSyncFromTpaService;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BaseEventAction {

    public static final String CLAIM_STATUS = "claimStatus";
    public static final String CLAIM_STATUS_DESC = "claimStatusDesc";


    public static final String BIZ_IDENTITY_CODE = "bizIdentityCode";

    public static final String TENANT_ID="tenantId";

    @Autowired
    protected ClaimSyncFromTpaService syncFromTpaService;
    @Autowired
    protected ClaimService claimService;

    @Autowired
    protected CommonLogService commonLogService;
    @Autowired
    protected ClaimRepository claimRepository;
    @Autowired
    protected InvoiceProjectRepository projectRepository;


    @Autowired
    protected InvoiceProjectItemRepository itemRepository;


    @Autowired
    protected ClaimStakeholderRepository stakeholderRepository;


    protected void clearOperator(Claim upDto){
        upDto.setOperatorUserName("");
        upDto.setOperatorUserId("");
        upDto.setOperatorOrgId("");
        upDto.setOperatorOrgName("");
    }
    /**
     * 是否能修改数据
     * @param claimNumber
     * @return
     */
    public boolean canEditData(Long claimNumber){
        Claim exist =  claimService.getById(claimNumber);
        if(exist == null){
            return true;
        }
        ClaimStatusEnum statusEnum =  ClaimStatusEnum.getByCode(exist.getStatus(),exist.getStatusSub());
        if( statusEnum == ClaimStatusEnum.ORC_INPUTING){
            return false;
        }
        if(statusEnum == ClaimStatusEnum.INSPECTIONING){
            return false;
        }
        if(statusEnum == ClaimStatusEnum.PUKANG_INPUTING){
            return false;
        }
        if(statusEnum == ClaimStatusEnum.PRE_ADUITING){
            return false;
        }

        return  true;
    }
}
