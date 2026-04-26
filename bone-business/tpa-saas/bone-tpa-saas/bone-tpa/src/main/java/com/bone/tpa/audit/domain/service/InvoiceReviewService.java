package com.bone.tpa.audit.domain.service;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.bone.core.util.BizContextUtils;
import com.bone.core.util.JsonUtil;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.audit.application.request.BatchUpdateAmountDTO;
import com.bone.tpa.audit.application.request.BatchUpdateDutyDTO;
import com.bone.tpa.audit.application.request.CopyInvoiceDTO;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.domain.service.ClaimTrackLogService;
import com.bone.tpa.sdk.adjustment.model.Plan;
import com.bone.tpa.sdk.adjustment.model.Policy;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.claim.model.InvoiceProject;
import com.bone.tpa.sdk.claim.model.InvoiceProjectItem;
import com.bone.tpa.sdk.dao.ClaimInvoiceRepository;
import com.bone.tpa.sdk.dao.InvoiceProjectRepository;
import com.bone.tpa.sdk.dao.impl.InvoiceProjectItemRepository;
import com.bone.tpa.sdk.dao.impl.PolicyRepository;
import com.bone.tpa.sdk.service.ClaimInfoService;
import com.bone.tpa.sdk.service.LiabilityInfoBasicService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InvoiceReviewService {

    @Autowired
    private ClaimInvoiceRepository invoiceRepository;

    @Autowired
    private ClaimTrackLogService claimTrackLogService;

    @Autowired
    private ClaimInfoService claimInfoService;

    @Autowired
    private LiabilityInfoBasicService liabilityInfoBasicService;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private InvoiceProjectRepository invoiceProjectRepository;

    @Autowired
    private InvoiceProjectItemRepository invoiceProjectItemRepository;

    @Autowired
    private ClaimService claimService;

    @Transactional(rollbackFor = Exception.class)
    public void updateInvoiceDuty(BatchUpdateDutyDTO batchUpdateDutyDTO) {
        Claim claim = claimInfoService.getClaim(batchUpdateDutyDTO.getClaimId());
        if (ObjectUtil.compare(claim.getStatus(), ClaimStatusEnum.COMPLETE_AUDIT.getCode()) >= 0) {
            throw new TpaBizException("只有待审核状态的赔案才能更新责任");
        }
        if (claim.getPlanUuid() == null) {
            throw new TpaBizException("请先关联保单！");
        }
        Criteria<Policy> criteria = Criteria.create();
        criteria.eq(Policy::getPolicyNo, batchUpdateDutyDTO.getPolicyNo());
        List<Policy> policyList = policyRepository.findByCriteria(criteria);
        if (CollectionUtil.isEmpty(policyList)) {
            throw new TpaBizException("该赔案关联保单不存在");
        }
        Plan plan = liabilityInfoBasicService.getPlan(claim.getPlanUuid());
        List<LiabilityConfig> liabilityConfigs = liabilityInfoBasicService.queryLiabilityByUuidAndVersion(batchUpdateDutyDTO.getPolicyNo(), batchUpdateDutyDTO.getDutyIds(), plan.getVersion());
        if (liabilityConfigs.size() != batchUpdateDutyDTO.getDutyIds().size()) {
            throw new TpaBizException("责任配置不存在");
        }
        Criteria<ClaimInvoice> invoiceCriteria = new Criteria<>();
        invoiceCriteria.eq(ClaimInvoice::getBizIdentityCode, claim.getBizIdentityCode());
        invoiceCriteria.eq(ClaimInvoice::getTenantId, claim.getTenantId());
        invoiceCriteria.in(ClaimInvoice::getId, batchUpdateDutyDTO.getInvoiceIds());
        List<ClaimInvoice> oldInvoices = invoiceRepository.findByCriteria(invoiceCriteria);
        ClaimInvoice invoice = new ClaimInvoice();
        invoice.setRelateLiability(batchUpdateDutyDTO.getDutyIds().stream().collect(Collectors.joining(",")));
        invoiceRepository.updateByCriteria(invoice, invoiceCriteria);

        claimTrackLogService.claimRecord(claim, BizContextUtils.getUser(), JsonUtil.toJson(oldInvoices),
                JsonUtil.toJson(batchUpdateDutyDTO), OperationTypeEnum.UPDATE, BizModelEnum.CLAIM_INVOICE, "批量责任");
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateInvoiceAmount(BatchUpdateAmountDTO batchUpdateAmountDTO) {
        Claim claim = claimInfoService.getClaim(batchUpdateAmountDTO.getClaimId());
        if (ObjectUtil.compare(claim.getStatus(), ClaimStatusEnum.COMPLETE_AUDIT.getCode()) >= 0) {
            throw new TpaBizException("只有待审核状态的赔案才能批量拒赔");
        }
        if (claim.getPlanUuid() == null) {
            throw new TpaBizException("请先关联保单！");
        }

        Criteria<ClaimInvoice> invoiceCriteria = new Criteria<>();
        invoiceCriteria.in(ClaimInvoice::getId, batchUpdateAmountDTO.getInvoiceIds());
        List<ClaimInvoice> oldInvoices = invoiceRepository.findByCriteria(invoiceCriteria);
        List<ClaimInvoice> newInvoices = oldInvoices.stream().map(invoice -> {
            ClaimInvoice newInvoice = new ClaimInvoice();
            newInvoice.setId(invoice.getId());
            BigDecimal amount = BigDecimal.ZERO;
            if (ObjectUtil.compare(BigDecimal.ZERO, invoice.getInvalidAmount()) < 0) {
                amount = amount.add(invoice.getInvalidAmount());
            }
            if (ObjectUtil.compare(BigDecimal.ZERO, invoice.getValidAmount()) < 0) {
                amount = amount.add(invoice.getValidAmount());
                newInvoice.setValidAmount(BigDecimal.ZERO);
            }
            if (ObjectUtil.compare(BigDecimal.ZERO, invoice.getTotalSelfPayAmount()) < 0) {
                amount = amount.add(invoice.getTotalSelfPayAmount());
                newInvoice.setTotalSelfPayAmount(BigDecimal.ZERO);
            }
            if (ObjectUtil.compare(BigDecimal.ZERO, invoice.getSelfPayPart2Amount()) < 0) {
                amount = amount.add(invoice.getSelfPayPart2Amount());
                newInvoice.setSelfPayPart2Amount(BigDecimal.ZERO);
            }
            if (ObjectUtil.compare(BigDecimal.ZERO, invoice.getClassCSelfPayAmount()) < 0) {
//                amount = amount.add(invoice.getClassCSelfPayAmount());
                newInvoice.setClassCSelfPayAmount(BigDecimal.ZERO);
            }
            if (ObjectUtil.compare(BigDecimal.ZERO, invoice.getExcessLimitSelfPayAmount()) < 0) {
//                amount = amount.add(invoice.getExcessLimitSelfPayAmount());
                newInvoice.setExcessLimitSelfPayAmount(BigDecimal.ZERO);
            }
            newInvoice.setInvalidAmount(amount);
            invoiceRepository.update(newInvoice);
            return invoice;
        }).collect(Collectors.toList());

        claimTrackLogService.claimRecord(claim, BizContextUtils.getUser(), JsonUtil.toJson(oldInvoices),
                JsonUtil.toJson(newInvoices), OperationTypeEnum.UPDATE, BizModelEnum.CLAIM_INVOICE, "批量拒赔");
    }

    @Transactional(rollbackFor = Exception.class)
    public void copyInvoice(CopyInvoiceDTO copyInvoiceDTO) {
        Claim claim = claimInfoService.getClaim(copyInvoiceDTO.getClaimId());
        if (ObjectUtil.compare(claim.getStatus(), ClaimStatusEnum.COMPLETE_AUDIT.getCode()) >= 0) {
            throw new TpaBizException("只有待审核状态的赔案才能复制发票");
        }
        ClaimInvoice oldClaimInvoice = invoiceRepository.findById(copyInvoiceDTO.getInvoiceId());
        if (ObjectUtil.isNull(oldClaimInvoice)) {
            throw new TpaBizException("原发票不存在");
        }

        ClaimInvoice newClaimInvoice = new ClaimInvoice();
        BeanUtils.copyProperties(oldClaimInvoice, newClaimInvoice);
        newClaimInvoice.setId(null);
        newClaimInvoice.setInvoiceNo(copyInvoiceDTO.getUpdateInvoiceNo());
        newClaimInvoice.setInvoiceUuid(UUID.randomUUID().toString());
        Long newInvoiceId = invoiceRepository.insert(newClaimInvoice);

        Criteria<InvoiceProject> invoiceProjectCriteria = new Criteria<>();
        invoiceProjectCriteria.eq(InvoiceProject::getTenantId, claim.getTenantId());
        invoiceProjectCriteria.eq(InvoiceProject::getBizIdentityCode, claim.getBizIdentityCode());
        invoiceProjectCriteria.eq(InvoiceProject::getRelatedId, copyInvoiceDTO.getInvoiceId());
        List<InvoiceProject> invoiceProjectList = invoiceProjectRepository.findByCriteria(invoiceProjectCriteria);
        if (CollectionUtil.isNotEmpty(invoiceProjectList)) {
            List<InvoiceProject> newProjectList = new ArrayList<>();
            invoiceProjectList.forEach(invoiceProject -> {
                InvoiceProject newInvoiceProject = new InvoiceProject();
                BeanUtils.copyProperties(invoiceProject, newInvoiceProject);
                newInvoiceProject.setRelatedId(newInvoiceId);
                newProjectList.add(newInvoiceProject);
            });
            invoiceProjectRepository.saveBatch(newProjectList);
        }

        Criteria<InvoiceProjectItem> invoiceItemCriteria = new Criteria<>();
        invoiceItemCriteria.eq(InvoiceProjectItem::getTenantId, claim.getTenantId());
        invoiceItemCriteria.eq(InvoiceProjectItem::getBizIdentityCode, claim.getBizIdentityCode());
        invoiceItemCriteria.eq(InvoiceProjectItem::getRelatedId, copyInvoiceDTO.getInvoiceId());
        List<InvoiceProjectItem> invoiceProjectItemList = invoiceProjectItemRepository.findByCriteria(invoiceItemCriteria);
        if (CollectionUtil.isNotEmpty(invoiceProjectItemList)) {
            List<InvoiceProjectItem> newProjectItemList = new ArrayList<>();
            invoiceProjectItemList.forEach(invoiceProjectItem -> {
                InvoiceProjectItem newInvoiceProjectItem = new InvoiceProjectItem();
                BeanUtils.copyProperties(invoiceProjectItem, newInvoiceProjectItem);
                newInvoiceProjectItem.setItemUuid(UUID.randomUUID().toString());
                newInvoiceProjectItem.setRelatedId(newInvoiceId);
                newInvoiceProjectItem.setRelatedInvoiceNo(copyInvoiceDTO.getUpdateInvoiceNo());
                newProjectItemList.add(newInvoiceProjectItem);
            });
            invoiceProjectItemRepository.saveBatch(newProjectItemList);
        }

        claimTrackLogService.claimRecord(claim, BizContextUtils.getUser(), JsonUtil.toJson(oldClaimInvoice),
                JsonUtil.toJson(newClaimInvoice), OperationTypeEnum.COPY_INVOICE, BizModelEnum.CLAIM_INVOICE, "审核时复制发票"+oldClaimInvoice.getInvoiceNo()+"->"+ copyInvoiceDTO.getUpdateInvoiceNo());

        // upload es
        claimService.checkSameInvoice(copyInvoiceDTO.getClaimId());
    }
}
