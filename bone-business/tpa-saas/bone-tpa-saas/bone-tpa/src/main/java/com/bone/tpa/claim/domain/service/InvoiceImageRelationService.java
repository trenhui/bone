package com.bone.tpa.claim.domain.service;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.sdk.claim.model.ClaimImage;
import com.bone.tpa.sdk.dao.InvoiceImageRelationRepository;
import com.bone.tpa.sdk.claim.model.InvoiceImageRelation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * ss_claim Service 接口
 *
 * @author 0
 */
@Service
@Slf4j
public class InvoiceImageRelationService {
    @Autowired
    private InvoiceImageRelationRepository invoiceImageRelationRepository;

    public List<InvoiceImageRelation> getListByClaimId(Long claimId){
        Criteria<InvoiceImageRelation> criteria = new Criteria();
        criteria.eq(InvoiceImageRelation::getClaimId, claimId);

        return invoiceImageRelationRepository.findByCriteria(criteria);
    }

    public void insertBatch(List<InvoiceImageRelation> invoiceImageRelationList) {
        invoiceImageRelationRepository.insertBatch(invoiceImageRelationList);
    }

    /**
     * 根据影像件的uuid查询获取
     * @param imageDetailId
     * @return
     */
    public List<InvoiceImageRelation> getByImageDetailId(List<String> imageDetailId) {
        Criteria<InvoiceImageRelation> criteria = Criteria.create();
        criteria.in(InvoiceImageRelation::getImageDetailId, imageDetailId);

        return invoiceImageRelationRepository.findByCriteria(criteria);
    }

    /**
     * 根据发票的uuid查询获取
     * @param invoiceUuid
     * @return
     */
    public List<InvoiceImageRelation> getByInvoiceUuid(List<String> invoiceUuid) {
        Criteria<InvoiceImageRelation> criteria = Criteria.create();
        criteria.in(InvoiceImageRelation::getInvoiceUuid, invoiceUuid);

        return invoiceImageRelationRepository.findByCriteria(criteria);
    }


    /**
     * 删除影像件和发票关联
     *
     * @return 赔案详情
     */
    public void deleteRelation(List<Long> idList) {
        invoiceImageRelationRepository.deleteByIds(idList);
    }

    /**
     * 更新影像件详情
     *
     * @return 赔案详情
     */
    public void newRelation(Long claimId, String imageDetailId, List<String> invoiceUuidList) {
        List<InvoiceImageRelation> invoiceImageRelationList = new ArrayList<>();
        for (String invoiceUuid : invoiceUuidList) {
            InvoiceImageRelation invoiceImageRelation = new InvoiceImageRelation();
            invoiceImageRelation.setInvoiceUuid(invoiceUuid);
            invoiceImageRelation.setImageDetailId(imageDetailId);
            invoiceImageRelation.setClaimId(claimId);

            invoiceImageRelationList.add(invoiceImageRelation);
        }

        invoiceImageRelationRepository.insertBatch(invoiceImageRelationList);
    }


}
