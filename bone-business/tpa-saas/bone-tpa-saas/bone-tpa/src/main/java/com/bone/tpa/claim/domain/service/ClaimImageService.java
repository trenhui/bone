package com.bone.tpa.claim.domain.service;

import com.bone.core.result.QueryParam;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.claim.application.request.QueryListRequest;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.dao.ClaimImageRepository;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.ClaimImage;
import com.bone.tpa.sdk.claim.model.InvoiceImageRelation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * ss_claim Service 接口
 *
 * @author 0
 */
@Service
@Slf4j
public class ClaimImageService {
    @Autowired
    private ClaimImageRepository claimImageRepository;

    @Autowired
    private InvoiceImageRelationService relationService;


    /**
     * 根据查询选项获取表格信息
     * @param request
     * @return
     */
    public List<ClaimImage> getByQueryParam(QueryListRequest request) {
        request.getQueryParams().add(new QueryParam("tenantId", request.getTenantId()));
        request.getQueryParams().add(new QueryParam("relatedId", request.getId()));

        return claimImageRepository.queryByCondition(request.getQueryParams(), request.getSortingFields(), (request.getPageNo() - 1) * request.getPageSize(), request.getPageSize(),
                "ss_claim_image", request.getBizIdentityCode()).getData();
    }


    /**
     * 根据id列表获取
     * @param id
     * @return
     */
    public ClaimImage getById(Long id) {
        return claimImageRepository.findById(id);
    }

    /**
     * 根据id列表获取
     * @param idList
     * @return
     */
    public List<ClaimImage> getByIdList(List<Long> idList) {
        return claimImageRepository.findById(idList);
    }


    public List<ClaimImage> getListByClaimNumber(Long claimNumber){
        Criteria<ClaimImage> criteria = new Criteria();
        criteria.eq(ClaimImage::getRelatedId, claimNumber);

        return claimImageRepository.findByCriteria(criteria);
    }

    /**
     * 根据id列表更新
     * @return
     */
    public void updateClaimImageList(List<ClaimImage> claimImageList) {
        claimImageRepository.saveBatch(claimImageList);
    }

    public void insertBatch(List<ClaimImage> claimImageList) {
        claimImageRepository.insertBatch(claimImageList);
    }


    /**
     * 更新影像件详情
     *
     * @return 赔案详情
     */
    public void updateClaimImage(ClaimImage claimImage) {
        if (claimImage.getId() == null) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "影像件不存在");
        }

        claimImageRepository.update(claimImage);
    }

    public Long createNewClaimImage(ClaimImage claimImage) {
        //这里要做好多好多的检测
        if(StringUtils.isBlank(claimImage.getImageDetailId())){
            claimImage.setImageDetailId(UUID.randomUUID().toString());
        }
        Long id = claimImageRepository.insert(claimImage);
        return id;
    }


    public List<ClaimImage> queryByImageDetailId(List<String> imageDetailIdList) {
        Criteria<ClaimImage> criteria = new Criteria();
        criteria.in(ClaimImage::getImageDetailId, imageDetailIdList);

        List<ClaimImage> claimImageList = claimImageRepository.findByCriteria(criteria);

        return claimImageList;
    }


    /**
     * 删除影像件并且检查影像件是否
     * @param idList
     */
    public void deleteImage(List<Long> idList) {
        List<ClaimImage> claimImageList = claimImageRepository.findById(idList);

        //收集影像件uuid, 用于查询影像件和发票关联关系
        List<String> imageUuidList = claimImageList.stream().map(ClaimImage::getImageDetailId).toList();

        List<InvoiceImageRelation> relationList = relationService.getByInvoiceUuid(imageUuidList);
        List<Long> relationIdList = relationList.stream().map(InvoiceImageRelation::getId).toList();
        relationService.deleteRelation(relationIdList);


        claimImageRepository.deleteByIds(idList);
    }


    public Integer getBiggestIndex(Long claimId, Long tenantId) {
        QueryListRequest queryListRequest = new QueryListRequest();
        queryListRequest.setId(String.valueOf(claimId));
        queryListRequest.setTenantId(String.valueOf(tenantId));

        List<ClaimImage> claimImageList = getByQueryParam(queryListRequest);

        if (claimImageList == null || claimImageList.isEmpty()) {
            return 0;
        }

        Integer maxIndex = claimImageList.stream().map(ClaimImage::getImageIndex).toList().stream().max(Integer::compare).get();

        return maxIndex;
    }

}
