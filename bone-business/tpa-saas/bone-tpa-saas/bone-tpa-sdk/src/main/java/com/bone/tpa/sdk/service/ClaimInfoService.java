package com.bone.tpa.sdk.service;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.sdk.adjustment.exception.DataNotFoundException;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import com.bone.tpa.sdk.claim.model.InvoiceProjectItem;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.dao.ClaimStakeholderRepository;
import com.bone.tpa.sdk.dao.impl.ClaimInvoiceRepository;
import com.bone.tpa.sdk.dao.impl.InvoiceProjectItemRepository;
import com.bone.tpa.sdk.dao.impl.LiabilityRepository;
import com.bone.tpa.sdk.dao.impl.PlanRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 理算
 * 数据准备服务
 *
 * 负责查询理算的基础数据
 */
@Service
@Slf4j
public class ClaimInfoService {
    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private ClaimInvoiceRepository claimInvoiceRepository;

    @Autowired
    private ClaimStakeholderRepository claimStakeholderRepository;

    @Autowired
    private LiabilityRepository liabilityRepository;


    @Autowired
    private InvoiceProjectItemRepository invoiceProjectItemRepository;


    /**
     * 获取赔案
     */
    public Claim getClaim(Long claimId) {
        Claim claim = claimRepository.findById(claimId);
        if (claim == null) {
            throw new DataNotFoundException("未找到该赔案  claimId：" + claimId);
        }

        return claim;
    }

    /**
     * 获取相关人
     */
    public List<ClaimStakeholder> getStakeHolder(Long claimId, String bizIdentityCode, Long tenantId, String personType, boolean forceThrowException) {
        Criteria<ClaimStakeholder> criteria = Criteria.create();

        criteria.eq("relatedId", claimId)
                .eq("tenantId", tenantId)
                .eq("bizIdentityCode", bizIdentityCode);
        if (personType != null && !personType.isEmpty()) {
            criteria.eq("personType", personType);
        }

        List<ClaimStakeholder> stakeholderList = claimStakeholderRepository.findByCriteria(criteria);

        if (stakeholderList == null || stakeholderList.isEmpty()) {
            if (forceThrowException) {
                throw new DataNotFoundException("未找到该赔案的" + personType + ",  claimId：" + claimId);
            } else {
                return null;
            }
        }

        return stakeholderList;
    }

    /**
     * 获取发票列表
     */
    public List<ClaimInvoice> getInvoiceList(Long claimId, String bizIdentityCode, Long tenantId) {
        List<ClaimInvoice> invoiceList = getInvoiceListWithoutException(claimId, bizIdentityCode, tenantId);

        if (invoiceList == null || invoiceList.isEmpty()) {
            throw new DataNotFoundException("未找到该赔案的发票  claimId：" + claimId);
        }

        return invoiceList;
    }

    /**
     * 不抛出异常
     * @param claimId
     * @param bizIdentityCode
     * @param tenantId
     * @return
     */
    public List<ClaimInvoice> getInvoiceListWithoutException(Long claimId, String bizIdentityCode, Long tenantId) {
        Criteria<ClaimInvoice> criteria = Criteria.create();
        criteria.eq("relatedId", claimId)
                .eq("bizIdentityCode", bizIdentityCode)
                .eq("tenantId", tenantId);

        List<ClaimInvoice> invoiceList = claimInvoiceRepository.findByCriteria(criteria);
        return invoiceList;
    }

    /**
     * 获取发票列表
     */
    public List<ClaimInvoice> getInvoiceListByIdList(List<Long> invoiceIdList) {
        Criteria<ClaimInvoice> criteria = Criteria.create();
        criteria.in("id", invoiceIdList.toArray());

        List<ClaimInvoice> invoiceList = claimInvoiceRepository.findByCriteria(criteria);

        if (invoiceList == null || invoiceList.isEmpty()) {
            return new ArrayList<>();
        }

        return invoiceList;
    }

    /**
     * 获取项目详细信息列表
     */
    public List<InvoiceProjectItem> getItemList(Long invoiceId, String bizIdentityCode, Long tenantId) {
        Criteria<InvoiceProjectItem> criteria = Criteria.create();
        criteria.eq("relatedId", invoiceId)
                .eq("bizIdentityCode", bizIdentityCode)
                .eq("tenantId", tenantId);

        List<InvoiceProjectItem> itemList = invoiceProjectItemRepository.findByCriteria(criteria);

        if (itemList == null || itemList.isEmpty()) {
            throw new DataNotFoundException("未找到该发票的费用明细  invoiceId：" + invoiceId);
        }

        return itemList;
    }



}
