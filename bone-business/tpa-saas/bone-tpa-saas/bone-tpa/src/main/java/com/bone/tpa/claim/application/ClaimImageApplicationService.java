package com.bone.tpa.claim.application;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.exception.ServiceException;
import com.bone.core.result.QueryParam;
import com.bone.core.util.BizContextUtils;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.enums.HangUpStatus;
import com.bone.tpa.claim.application.converter.ClaimImageConverter;
import com.bone.tpa.claim.application.dto.ClaimImageDTO;
import com.bone.tpa.claim.application.dto.ImageTypeDTO;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.request.*;
import com.bone.tpa.claim.application.response.InvoiceBindResult;
import com.bone.tpa.claim.domain.service.*;
import com.bone.tpa.claim.infrastructure.log.SimpleLog;
import com.bone.tpa.facade.feign.TpaDataSyncFeign;
import com.bone.tpa.facade.request.PkbClaimImageRequest;
import com.bone.tpa.facade.request.TpaReleaseHangUpRequest;
import com.bone.tpa.facade.vo.InsuranceCompanyImageVO;
import com.bone.tpa.facade.vo.PkbImageResponse;
import com.bone.tpa.facade.vo.PkbImageVO;
import com.bone.tpa.push.feign.request.TpaPersonalImageQueryRequest;
import com.bone.tpa.push.feign.response.PersonalImageResponse;
import com.bone.tpa.push.service.ConfigSupportService;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.enums.ClaimStageEnum;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.*;
import com.bone.tpa.sdk.service.TimeLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 理赔案件应用层服务
 */
@Slf4j
@Service
@Transactional
@SimpleLog
public class ClaimImageApplicationService {
    @Autowired
    private ClaimImageConverter claimImageConverter;

    @Autowired
    private ClaimImageService claimImageService;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private ClaimInvoiceService claimInvoiceService;

    @Autowired
    private InvoiceImageRelationService invoiceImageRelationService;

    @Autowired
    private TpaDataSyncFeign tpaDataSyncFeign;

    @Autowired
    private ClaimTrackLogService claimTrackLogService;

    @Autowired
    private TimeLogService timeLogService;

    @Autowired
    private ClaimStakeholderService stakeholderService;

    @Autowired
    @Qualifier("saasConfigSupportServiceImpl")
    private ConfigSupportService configSupportService;

    //比较器
    Comparator<String> typeComparator = (s1, s2) -> {
        if(s1 == null || s1.isEmpty()) {
            return -1;
        }

        if(s2 == null || s2.isEmpty()) {
            return 1;
        }

        return Integer.parseInt(s1) - Integer.parseInt(s2);
    };

    @Transactional(rollbackFor = Throwable.class)
    public List<ClaimImageDTO> getClaimImage(QueryListRequest request) {
        //这里加一个特殊判定。如果当前的赔案阶段是录入，则已经被ocr识别的发票不展示
        Claim claim = claimService.getById(Long.valueOf(request.getId()));
        if (claim == null) {
            throw new TpaBizException(BizErrorCode.NO_RECORD, request.getId());
        }

        if (ClaimStageEnum.SUBMITTING.getCode().equals(claim.getStage())) {
            request.getQueryParams().add(new QueryParam("ocrFlag", 0));
        }

        List<ClaimImage> claimImageList = claimImageService.getByQueryParam(request);

        //进行排序
        List<ClaimImage> sortedList = claimImageList.stream()
                .sorted(Comparator
                        .comparing(ClaimImage::getImagePkType, typeComparator)
                        .thenComparing(ClaimImage::getImageType, typeComparator)
                        .thenComparing(ClaimImage::getImageIndex)
                ).collect(Collectors.toList());

        //转化为DTO返回
        List<ClaimImageDTO> claimImageDTOList = new ArrayList<>();
        for (ClaimImage claimImage : sortedList) {
            ClaimImageDTO claimImageDTO = claimImageConverter.toDTO(claimImage);
            claimImageDTOList.add(claimImageDTO);
        }

        return claimImageDTOList;
    }


    @Transactional(rollbackFor = Throwable.class)
    public List<ImageTypeDTO> getTypeList(String insuranceName) {
        List<InsuranceCompanyImageVO> insuranceCompanyImageVOList = getCompanyImageVO(insuranceName);

        List<ImageTypeDTO> imageTypeDTOList = new ArrayList<>();
        for (InsuranceCompanyImageVO imageVO : insuranceCompanyImageVOList) {
            ImageTypeDTO imageTypeDTO = new ImageTypeDTO();
            imageTypeDTO.setClassifyCode(imageVO.getImageClassifyCode());
            imageTypeDTO.setClassifyName(imageVO.getFieldName());
            imageTypeDTO.setClassifyPkCode(imageVO.getImageMapCode());
            imageTypeDTOList.add(imageTypeDTO);
        }

        return imageTypeDTOList;
    }


    @Transactional(rollbackFor = Throwable.class)
    public void updateClaimImageList(List<ClaimImageDTO> claimImageDTOList) {
        if (claimImageDTOList == null || claimImageDTOList.isEmpty()) {
            return;
        }

        Claim claim = claimService.getById(claimImageDTOList.get(0).getRelatedId());
        claimService.checkFinish(claim);
        claimService.checkUserId(claim);

        List<InsuranceCompanyImageVO> insuranceCompanyImageVOList = getCompanyImageVO(claim.getInsuranceName());

        //保司分类, 普康分类
        Map<String, String> imageTypeMap = insuranceCompanyImageVOList.stream().collect(Collectors.toMap(InsuranceCompanyImageVO::getImageClassifyCode, InsuranceCompanyImageVO::getImageMapCode));

        List<ClaimImage> claimImageList = new ArrayList<>();
        for (ClaimImageDTO claimImageDTO : claimImageDTOList) {
            ClaimImage claimImage = claimImageConverter.toEntity(claimImageDTO);
            if (!imageTypeMap.containsKey(claimImage.getImageType())) {
                throw new TpaBizException(BizErrorCode.CLASSIFY_CODE_ERROR, claimImage.getImageType());
            }
            claimImage.setImagePkType(imageTypeMap.get(claimImage.getImageType()));
            claimImage.setImageIndex(null);
            claimImageList.add(claimImage);
        }

        claimImageService.updateClaimImageList(claimImageList);

        //如果在这个时候赔案是挂起状态，触发解挂
        if (claim.getHangUpStatus() != null && claim.getHangUpStatus().equals(HangUpStatus.HANG_UP.getCode())) {
            claim.setHangUpStatus(HangUpStatus.NO_HANG_UP.getCode());
            claim.setHangUpType(null);
            claimService.updateClaim(claim, false, "赔案解挂");
            // 增加解挂操作记录
            claimTrackLogService.claimRecord(claim, BizContextUtils.getUser(), null, null, OperationTypeEnum.RELEASE_HANGUP, BizModelEnum.CLAIM_DETAIL, "更新影像件分类，赔案解挂");
            timeLogService.addTimeLog(claim, OperationTypeEnum.RELEASE_HANGUP, claim.getOperatorUserName());

            TpaReleaseHangUpRequest request = new TpaReleaseHangUpRequest();
            request.setClaimNo(claim.getClaimNo());
            request.setHangUpStatus("已处理");

            ApiResult<Map<String,Object>> remoteRs =   tpaDataSyncFeign.releaseClaimHangUpReord(request);
            log.info("解挂请求返回:{}", JSONObject.toJSONString(remoteRs));
            if(!remoteRs.isSuccess()){
                throw new RuntimeException("解挂失败:"+remoteRs.getMessage());
            }
        }
    }


    public List<InsuranceCompanyImageVO> getCompanyImageVO(String insuranceName) {
        ApiResult<List<InsuranceCompanyImageVO>> result = tpaDataSyncFeign.getImageMapDetail(insuranceName);
        if(!result.isSuccess()){
            throw new RuntimeException("获取保司分类失败:"+result.getMessage());
        }
        return result.getData();
    }


    @Transactional(rollbackFor = Throwable.class)
    public void deleteClaimImages(DeleteRequest request) {
        checkUserId(request.getIdList());

        //取出所有影像件相关的发票关联关系并且删除


        claimImageService.deleteImage(request.getIdList());
    }


    @Transactional(rollbackFor = Throwable.class)
    public void updatePushFlag(ImageUpdateRequest request) {
        checkUserId(request.getIdList());

        List<ClaimImage> claimImageList = claimImageService.getByIdList(request.getIdList());

        List<ClaimImage> claimImageUpdateList = new ArrayList<>();
        for (ClaimImage claimImage : claimImageList) {
            if (request.getPushFlag() && claimImage.getPushFlag() == 0) {
                claimImage.setPushFlag(1);
                claimImageUpdateList.add(claimImage);
            } else if (!request.getPushFlag() && claimImage.getPushFlag() == 1) {
                claimImage.setPushFlag(0);
                claimImageUpdateList.add(claimImage);
            }
        }

        claimImageService.updateClaimImageList(claimImageUpdateList);
    }


    @Transactional(rollbackFor = Throwable.class)
    public void updateOcrFlag(ImageUpdateRequest request) {
        checkUserId(request.getIdList());

        List<ClaimImage> claimImageList = claimImageService.getByIdList(request.getIdList());

        List<ClaimImage> claimImageUpdateList = new ArrayList<>();
        for (ClaimImage claimImage : claimImageList) {
            if (request.getOcrFlag() && claimImage.getOcrFlag() == 0) {
                claimImage.setOcrFlag(1);
                claimImageUpdateList.add(claimImage);
            } else if (!request.getOcrFlag() && claimImage.getOcrFlag() == 1) {
                claimImage.setOcrFlag(0);
                claimImageUpdateList.add(claimImage);
            }
        }

        claimImageService.updateClaimImageList(claimImageUpdateList);
    }


    @Transactional(rollbackFor = Throwable.class)
    public List<InvoiceBindResult> invoiceListForImageBound(Long imageId) {
        ClaimImage claimImage = claimImageService.getById(imageId);

        if (claimImage == null) {
            throw new TpaBizException(BizErrorCode.NO_RECORD, "赔案影像件不存在: " + imageId);
        }

        Claim claim = claimService.getById(claimImage.getRelatedId());

        QueryListRequest queryListRequest = new QueryListRequest();
        queryListRequest.setId(String.valueOf(claim.getId()));
        queryListRequest.setTenantId(String.valueOf(claim.getTenantId()));
        queryListRequest.setBizIdentityCode(claim.getBizIdentityCode());
        List<ClaimInvoice> claimInvoiceList = claimInvoiceService.getClaimInvoices(queryListRequest);

        List<InvoiceImageRelation> imageRelationList = invoiceImageRelationService.getByImageDetailId(Collections.singletonList(claimImage.getImageDetailId()));
        List<String> relatedUuidList = imageRelationList.stream().map(InvoiceImageRelation::getInvoiceUuid).toList();

        List<InvoiceBindResult> resultList = new ArrayList<>();
        for (ClaimInvoice claimInvoice : claimInvoiceList) {
            InvoiceBindResult result = new InvoiceBindResult();
            result.setInvoiceNo(claimInvoice.getInvoiceNo());
            result.setInvoiceId(claimInvoice.getId());
            result.setInvoiceUuid(claimInvoice.getInvoiceUuid());
            result.setBound(relatedUuidList.contains(claimInvoice.getInvoiceUuid()));

            resultList.add(result);
        }

        return resultList;
    }


    @Transactional(rollbackFor = Throwable.class)
    public List<InvoiceBindResult> imageBindInvoice(InvoiceBindRequest request) {
        ClaimImage claimImage = claimImageService.getById(request.getImageId());

        if (claimImage == null) {
            throw new TpaBizException(BizErrorCode.NO_RECORD, "赔案影像件不存在: " + request.getImageId());
        }

        Claim claim = claimService.getById(claimImage.getRelatedId());
        claimService.checkFinish(claim);
        claimService.checkUserId(claim);

        List<InvoiceImageRelation> imageRelationList = invoiceImageRelationService.getByImageDetailId(Collections.singletonList(claimImage.getImageDetailId()));
        Map<String, Long> relatedUuidMap = imageRelationList.stream().collect(Collectors.toMap(InvoiceImageRelation::getInvoiceUuid, InvoiceImageRelation::getId));

        // 遍历需要绑定的uuidList，将其从map中去除
        List<String> oldBindUuidList = new ArrayList<>();;
        for (String newBindUuid : request.getInvoiceUuidList()) {
            if (relatedUuidMap.containsKey(newBindUuid)) {
                relatedUuidMap.remove(newBindUuid);
                oldBindUuidList.add(newBindUuid);
            }
        }

        request.getInvoiceUuidList().removeAll(oldBindUuidList);

        // 删除不再绑定的发票
        invoiceImageRelationService.deleteRelation(relatedUuidMap.values().stream().toList());

        // 新增绑定的发票
        invoiceImageRelationService.newRelation(claim.getId(), claimImage.getImageDetailId(), request.getInvoiceUuidList());

        return invoiceListForImageBound(request.getImageId());
    }


    @Transactional(rollbackFor = Throwable.class)
    public void getPkbImage(GetPkbImageRequest request) {
        Claim claim = claimService.getById(request.getClaimId());
        claimService.checkFinish(claim);
        claimService.checkUserId(claim);

        PkbClaimImageRequest pkbClaimImageRequest = new PkbClaimImageRequest();
        pkbClaimImageRequest.setClaimNumber(Long.valueOf(claim.getClaimNo()));
        pkbClaimImageRequest.setPersonType(request.getPersonType());

        //调用tpa获取普康宝影像件
        ApiResult<PkbImageResponse> remoteRs = tpaDataSyncFeign.fetchPkbClaimImage(pkbClaimImageRequest);
        log.info("getReportingInformation: {}", JSONObject.toJSONString(remoteRs));
        if(!remoteRs.isSuccess()){
            //throw new TpaBizException(BizErrorCode.OUTER_CLIENT_ERROR ,"获取普康宝影像件接口调用失败:" + remoteRs.getMessage());
            throw new TpaBizException(BizErrorCode.OUTER_CLIENT_ERROR ,"获取普康宝影像件接口调用失败:" + remoteRs.getMessage());
        }

        PkbImageResponse pkbImageResponse = remoteRs.getData();
        if (pkbImageResponse == null || pkbImageResponse.getImages() == null || pkbImageResponse.getImages().isEmpty()) {
            return;
        }

        List<String> imageDetailIdList = pkbImageResponse.getImages().stream().map(PkbImageVO::getClaimImageId).toList();

        //根据uuid查询出所有的影像件
        List<ClaimImage> claimImageList = claimImageService.queryByImageDetailId(imageDetailIdList);

        Map<String, ClaimImage> claimImageMap = claimImageList.stream().collect(Collectors.toMap(ClaimImage::getImageDetailId, t -> t));

        //将普康宝影像件处理后插入到saas的数据库
        log.info("共新增{}张影像件", pkbImageResponse.getImages().size());
        Integer maxIndex = claimImageService.getBiggestIndex(claim.getId(), claim.getTenantId());
        for (PkbImageVO imageVO : pkbImageResponse.getImages()) {
            //如果该影像件已经存在，则只是更新
            if (claimImageMap.containsKey(imageVO.getClaimImageId())) {
                ClaimImage claimImage = claimImageMap.get(imageVO.getClaimImageId());
                claimImage.setImageName(imageVO.getImageName());
                claimImage.setImagePath(imageVO.getImagePath());
                //claimImage.setImageIndex(imageVO.getImageIndex());


                claimImage.setTenantId(claim.getTenantId());
                claimImage.setOcrFlag(0);
                claimImage.setSourceSystem(1);
                claimImage.setPushFlag(1);
                claimImageService.updateClaimImage(claimImage);
            } else {
                ClaimImage claimImage = new ClaimImage();
                claimImage.setImageName(imageVO.getImageName());
                claimImage.setImagePath(imageVO.getImagePath());
                claimImage.setImageIndex(++maxIndex);
                claimImage.setImageDetailId(imageVO.getClaimImageId());
                claimImage.setRelatedId(claim.getId());
                claimImage.setTenantId(claim.getTenantId());
                claimImage.setOcrFlag(0);
                claimImage.setSourceSystem(1);
                claimImage.setPushFlag(1);
                claimImageService.createNewClaimImage(claimImage);
            }
        }
    }


    /**
     * 检查用户名
     *
     * @param idList
     */
    private void checkUserId(List<Long> idList) {
        if (idList == null || idList.isEmpty()) {
            return;
        }
        Long imageId = idList.get(0);
        ClaimImage claimImage = claimImageService.getById(imageId);
        Claim claim = claimService.getById(claimImage.getRelatedId());
        claimService.checkFinish(claim);
        claimService.checkUserId(claim);
    }

    public List<Long> addPersonalImageToClaim(Long claimNumber) {
        Claim claim = claimService.getById(claimNumber);
        if (claim == null) {
            throw new ServiceException(500, "赔案不存在,claimNumber:" + claimNumber);
        }

        ClaimStakeholder outInsurePeople = stakeholderService.getOutInsurePeople(claimNumber);
        String identityNo = outInsurePeople.getIdentityNo();
        String name = outInsurePeople.getName();
        String insuranceName = claim.getInsuranceName();
        List<PersonalImageResponse> personalImages = configSupportService.queryPersonalClaimImages(new TpaPersonalImageQueryRequest(identityNo, name, insuranceName));
        if (CollectionUtils.isEmpty(personalImages)) {
            throw new ServiceException(500, "个人影像库不存在数据,证件号:" + identityNo + ",出险人姓名:" + name + ",保险公司名称:" + insuranceName);
        }

        Map<String, List<InsuranceCompanyImageVO>> typeMap = new HashMap<>();
        try {
            ApiResult<List<InsuranceCompanyImageVO>> remoteCategory = tpaDataSyncFeign.getImageMapDetail(insuranceName);
            if (remoteCategory.isSuccess() && !CollectionUtils.isEmpty(remoteCategory.getData())) {
                List<InsuranceCompanyImageVO> typeVOList = remoteCategory.getData();
                //根据保司分类分组
                typeMap = typeVOList.stream().collect(Collectors.groupingBy(InsuranceCompanyImageVO::getImageClassifyCode));
            }
        } catch (Exception e) {
            log.error("调用tpa影像分类接口发生异常,claimNumber:{}", claimNumber, e);
        }

        List<Long> ids = new ArrayList<>();
        for (PersonalImageResponse personalImage : personalImages) {
            ClaimImage image = new ClaimImage();
            image.setTenantId(claim.getTenantId());
            image.setRelatedId(claimNumber);
            image.setImageDetailId(UUID.randomUUID().toString());
            image.setSourceSystem(1);
            String imagePath = personalImage.getImagePath();
            image.setImagePath(imagePath);
            if (StringUtils.hasText(imagePath)) {
                int index = imagePath.lastIndexOf("/");
                image.setImageName(index >= 0 ? imagePath.substring(index + 1) : "异常影像件地址:" + imagePath);
            }
            String imageType = personalImage.getImageType();//保司分类
            image.setImageType(imageType);
            if (StringUtils.hasText(imageType) && !CollectionUtils.isEmpty(typeMap)) {
                List<InsuranceCompanyImageVO> typeList = typeMap.get(imageType);
                if (!CollectionUtils.isEmpty(typeList) && typeList.size() == 1) {
                    image.setImagePkType(typeList.get(0).getImageMapCode());
                } else {
                    List<String> pkTypeList = CollectionUtils.isEmpty(typeList) ? List.of() : typeList.stream().map(InsuranceCompanyImageVO::getImageMapCode).toList();
                    String msg = MessageFormat.format("保司分类code:{0},普康分类code:{1}", imageType, pkTypeList.toString());
                    log.error("影像件分类失败,因为此保司分类对应0个或超过1个普康分类," + msg);
                }
            }
            image.setClearType(1);
            image.setRemark("通过添加个人库影像操作生成,赔案号:" + claimNumber);
            image.setOcrFlag(0);
            image.setPushFlag(0);
            image.setFromPersonalImage(1);
            image.setCreateTime(new Date());
            Long id = claimImageService.createNewClaimImage(image);
            ids.add(id);
        }
        log.info("新增来自个人影像库的赔案影像的id列表:{}", ids);
        return ids;
    }
}
