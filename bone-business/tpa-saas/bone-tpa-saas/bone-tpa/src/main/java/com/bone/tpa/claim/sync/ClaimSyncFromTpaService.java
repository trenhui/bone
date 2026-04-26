package com.bone.tpa.claim.sync;


import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONObject;
import com.bone.core.result.Result;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.enums.FieldModelDefine;
import com.bone.core.util.PkListUtil;
import com.bone.core.util.SnowUtil;
import com.bone.tpa.api.enums.*;
import com.bone.tpa.api.request.SyncClaimWithEventRequest;
import com.bone.tpa.api.vo.*;
import com.bone.tpa.claim.application.response.*;
import com.bone.tpa.claim.application.transfer.PersonFieldTransfer;
import com.bone.tpa.claim.application.transfer.PersonHead;
import com.bone.tpa.claim.application.transfer.PersonHolderConstants;
import com.bone.tpa.claim.domain.ext.strategy.YongChengUpdateRule;
import com.bone.tpa.claim.sync.Constant.*;
import com.bone.tpa.claim.sync.convert.SyncClaimConvert;
import com.bone.tpa.claim.sync.convert.SyncInvoiceConvert;
import com.bone.tpa.core.util.ExtraStoreUtil;
import com.bone.tpa.facade.vo.ColletionBindVO;
import com.bone.tpa.facade.vo.OptionSetDTO;
import com.bone.tpa.facade.vo.PageBizIdentityVO;
import com.bone.tpa.hook.ClaimHookUtil;
import com.bone.tpa.hook.inter.SyncFromTpaPreDealHook;
import com.bone.tpa.hook.vo.FromTpaPreHookParam;
import com.bone.tpa.sdk.claim.enums.PersonTypeEnum;
import com.bone.tpa.sdk.claim.enums.SignStatusEnum;
import com.bone.tpa.sdk.claim.model.*;
import com.bone.tpa.sdk.identityRule.invoice.update.OnSaveInvoiceHook;
import com.bone.tpa.sdk.util.ObjInvoke;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class    ClaimSyncFromTpaService extends SyncBaseTool {

    @Autowired
    protected SyncClaimConvert claimConvert;
    @Autowired
    protected SyncInvoiceConvert invoiceConvert;

    @Autowired
    private ClaimHookUtil claimHookUtil;

    @Autowired
    private YongChengUpdateRule yongChengUpdateRule;

    public void saveSyncImage(String bizIdentityCode,ClaimDetailSyncVO syncVO){
        Context context = new Context();
        context.setBizIdentityCode(bizIdentityCode);

        saveImageList(syncVO,  context,false);
        //保存影像件-发票关系
        saveImageRelation(syncVO,  context);
    }

    public void saveSyncVo(ClaimDetailSyncVO syncVO , ClaimConfig claimConfig){
        if( syncVO == null){
            throw new RuntimeException("saveSyncVo syncVO is null");
        }
        ClaimHeadInfo headInfo = syncVO.getClaimHeadInfo();
        if (headInfo == null) {
            throw new IllegalArgumentException("headInfo is null");
        }
        Long headerNumber = headInfo.getClaimheadnumber();
        if (headerNumber == null) {
            throw new IllegalArgumentException("headerNumber is null");
        }
        if (syncVO.getClaimNumber() == null) {
            throw new IllegalArgumentException("claimNumber is null");
        }
        String lockKey = String.format("%s_saveSyncVo_saas",headerNumber);



        Claim exist =   claimRepository.findById(syncVO.getClaimNumber());
        boolean isInsert =   exist== null?true:false;
        String policyNo = syncVO.getPolicyNo();
        String bizIdentityCode = null;
        if( isInsert){
            if (StringUtils.isBlank(policyNo)) {
                throw new IllegalArgumentException("policyNo is null");
            }
            if(StringUtils.isBlank(syncVO.getBranchName())){
                throw new IllegalArgumentException("branchName is null");
            }
            if(StringUtils.isBlank(syncVO.getInsuranceName())){
                throw new IllegalArgumentException("insuranceName is null");
            }
            if(StringUtils.isBlank(syncVO.getInsureName())){
                throw new IllegalArgumentException("insureName is null");
            }
            bizIdentityCode = calBizIdentity(policyNo,syncVO.getInsureName(),syncVO.getBranchName(),syncVO.getInsuranceName());
            if (bizIdentityCode == null) {
                throw new IllegalArgumentException("无法根据保单号确定业务主体:" + policyNo);
            }
        }else{
            bizIdentityCode = exist.getBizIdentityCode();
        }

        //进行保司特定规则检查和处理，很蠢的办法，后面修正
        //还加了一个逻辑，对领款人银行分公司的处理
       /* List<SyncFromTpaPreDealHook>  preDealHookList = claimHookUtil.getSyncFromTpaPreDealHookList(bizIdentityCode);
        for(SyncFromTpaPreDealHook hook : preDealHookList){
            hook.onUpdateClaimWhileFromTpa(syncVO,bizIdentityCode);
        }*/
        Context context = new Context();
        context.setBizIdentityCode(bizIdentityCode);
        context.setClaimNumber(syncVO.getClaimNumber());
        //设置扩展字段到上下文，后面使用
        context.setClaimExtMap(getExtendConfig(bizIdentityCode, FieldModelDefine.赔案));
        context.setInvoiceExtMap(getExtendConfig(bizIdentityCode, FieldModelDefine.发票));
        // context.setProjectExtMap(getExtendConfig(bizIdentityCode, FieldModelDefine.发票项目信息));
        context.setProjectItemExtMap(getExtendConfig(bizIdentityCode, FieldModelDefine.发票费用明细));
        context.setCollectPersonExtMap(getExtendConfig(bizIdentityCode, FieldModelDefine.领款人信息));
        context.setCollectBussinessExtMap(getExtendConfig(bizIdentityCode, FieldModelDefine.领款企业信息));
        context.setMainInsurePersonExtMap(getExtendConfig(bizIdentityCode, FieldModelDefine.主被保险人));
        context.setOutInsurePersonExtMap(getExtendConfig(bizIdentityCode, FieldModelDefine.出险人));
        context.setBenifitPersonExtMap(getExtendConfig(bizIdentityCode, FieldModelDefine.受益人));
        context.setSignRecordExtMap(getExtendConfig(bizIdentityCode, FieldModelDefine.签署记录));

        Map<String, ColletionBindVO>  allCollectionBind=   loadCollectionBind(bizIdentityCode,PkListUtil.newArrayList());
        context.setAllOptionSetMap(allCollectionBind);
        if(allCollectionBind != null){
            log.info("allCollectionBind value:{}", JSONObject.toJSONString(allCollectionBind));
        }
       // yongChengUpdateRule.onUpdateClaimWhileFromTpa(syncVO, bizIdentityCode,context,new HashMap<>());
        List<SyncFromTpaPreDealHook>  preDealHookList =    claimHookUtil.getSyncFromTpaPreDealHookList(bizIdentityCode);
        FromTpaPreHookParam preHookParam = new FromTpaPreHookParam();
        preHookParam.setSyncVO(syncVO);
        preHookParam.setContext(context);
        preHookParam.setBizIdentityCode(bizIdentityCode);
        preHookParam.setHintMap(new HashMap<>());
        for(SyncFromTpaPreDealHook hook : preDealHookList){
            hook.doEvent(preHookParam);
        }
        Boolean locked =  redisLockManage.tryLockAndWait(lockKey,10,10);
        if(!locked){
            throw new RuntimeException("并发操作中,headerNumber:"+headerNumber);
        }
        try {
            SignRecord signRecord = signRecordRepository.findById(headerNumber);
            String hasInsertKey = String.format("%s_claimHeader_saveSyncVo_saas",headerNumber);
            String hasHeadInsertag =  stringRedisTemplate.opsForValue().get(hasInsertKey);
            //先插入header信息
            if (signRecord == null && StringUtils.isBlank(hasHeadInsertag)) {
                //插入赔案批次
                SignRecord inserSign = new SignRecord();
                inserSign.setId(headerNumber);
                inserSign.setBatchNo(headerNumber.toString());
                inserSign.setClaimCount(headInfo.getClaimcount());
                inserSign.setBizIdentityCode(bizIdentityCode);
                inserSign.setTenantId(TENANT_ID);
                inserSign.setInsuranceCompany(headInfo.getInsurancename());
                inserSign.setInsuranceSubsidiary(headInfo.getBranchname());
                //渠道，比如线上线下
                inserSign.setSignType(syncVO.getSourceType());
                //渠道中文名，比如： 永诚好管家
                inserSign.setSignChannel(syncVO.getSourceCompanyName());
                //签收人姓名（中文)
                inserSign.setSignOperator(headInfo.getClaimsigncreator());
                //签收时间
                inserSign.setSignTime(inserSign.getSignTime());
                inserSign.setSignStatus(SignStatusEnum.FINISHED.getCode());
                signRecordRepository.insert(inserSign);
                inserSign = signRecordRepository.findById(headerNumber);
                Assert.notNull(inserSign, "插入赔案头数据失败");
                stringRedisTemplate.opsForValue().set(hasInsertKey,"1");
            }
            //context.setSignRecord(signRecord);

        } finally {
            redisLockManage.unlock(lockKey);
        }

        //保持赔案
        saveClaim(syncVO, claimConfig, context);
        //保存发票，项目，费用信息
        saveInvoice(syncVO,  context);
        //保存主被保险人信息
        saveMainInsurePerson(syncVO, context);
        //保存受益人信息
        saveBenifitPerson(syncVO, context);
        //保存出险人信息
        saveOutnsurePerson(syncVO, context);

        //申请人
        saveApplyPerson(syncVO, context);
        ////保存领款人信息
        // TODO: 保存领款企业信息
        saveCollectPerson(syncVO,  context);
        //保存影像件

        saveImageList(syncVO, context,true);
        //保存影像件-发票关系
        saveImageRelation(syncVO, context);
    }
    /**
     * 转化
     *
     * @param
     */
    @Transactional(rollbackFor = Exception.class)
    public void transfer(SyncClaimWithEventRequest request) {
        ClaimDetailSyncVO syncVO = request.getClaimInfo();
        ClaimConfig claimConfig = request.getClaimConfig();
        saveSyncVo(syncVO, claimConfig);

    }


    /**
     * 保存发票和影像的关系
     *
     * @param syncVO
     * @param claimConfig
     * @param context
     */
    private void saveImageRelation(ClaimDetailSyncVO syncVO,
                                   Context context) {

        List<ImageBindInvoice> tpaBindList = syncVO.getImageBindList();
        if (tpaBindList == null) {
            return;
        }
        for (ImageBindInvoice tpaBind : tpaBindList) {
            if(tpaBind.getId() == null){
                throw new RuntimeException("影像件绑定的id是空:"+tpaBind.getImageUuid());
            }
        }
        Criteria<InvoiceImageRelation> criteriaImageBind = new Criteria();
        Map<String, InvoiceImageRelation> existRelationMap = new HashMap<>();
        criteriaImageBind.eq(InvoiceImageRelation::getClaimId, syncVO.getClaimNumber());
        List<InvoiceImageRelation> existBindList = imageRelationRepository.findByCriteria(criteriaImageBind, true);

        for (InvoiceImageRelation existBind : existBindList) {
            existRelationMap.put(existBind.getId().toString(), existBind);
            if (existBind.getDeleted()) {
                continue;
            }
            imageRelationRepository.deleteById(existBind.getId());
        }


        for (ImageBindInvoice tpaBind : tpaBindList) {
            String imageUuid = tpaBind.getImageUuid();
            String invoiceUuid = tpaBind.getInvoiceUuid();
            String checkExistKey =tpaBind.getId().toString();
            InvoiceImageRelation exist = existRelationMap.get(checkExistKey);
            if (exist != null) {
                exist.setDeleted(false);
                imageRelationRepository.update(exist);
                continue;
            }
            exist = new InvoiceImageRelation();
            exist.setId(tpaBind.getId());
            exist.setClaimId(syncVO.getClaimNumber());
            exist.setImageDetailId(imageUuid);
            exist.setInvoiceUuid(invoiceUuid);
            imageRelationRepository.save(exist);
        }
    }

    private String getRelationmapKey(String imageUuid, String invoiceUuid) {
        return imageUuid + "_" + invoiceUuid;
    }

    /**
     * 保存影像件
     */
    private void saveImageList(ClaimDetailSyncVO syncVO,   Context context,boolean updateImageType) {
        List<ImageInfo> tpaImageList = syncVO.getImageList();
        if (tpaImageList == null) {
            return;
        }
        Criteria<ClaimImage> criteria = new Criteria();
        criteria.eq(ClaimImage::getRelatedId, syncVO.getClaimNumber());
        List<ClaimImage> allList = imageRepository.findByCriteria(criteria, true);
        //全部删除
        for (ClaimImage image : allList) {
            if (image.getDeleted()) {
                continue;
            }
            imageRepository.deleteById(image.getId());
        }
        Map<String, ClaimImage> imageUuidMapping = PkListUtil.listToMap(allList, ClaimImage::getImageDetailId);

        for (ImageInfo tpaImage : tpaImageList) {
            String imageUuid = tpaImage.getImageUuid();
            ClaimImage exist = imageUuidMapping.get(imageUuid);
            if (exist == null) {
                exist = new ClaimImage();
                exist.setRelatedId(syncVO.getClaimNumber());
                exist.setImageDetailId(imageUuid);
                exist.setSourceSystem(0);
                exist.setTenantId(TENANT_ID);
            }
            exist.setDeleted(false);
            exist.setImageName(tpaImage.getImageName());
            exist.setImagePath(tpaImage.getImagePath());
            exist.setImageIndex(tpaImage.getImageIndex());
            if(updateImageType){
                exist.setImageType(tpaImage.getImageType());
                exist.setImagePkType(tpaImage.getImagePkType());
            }

            if( exist .getImagePkType()  == null){
                exist.setImagePkType("1");
            }
            exist.setOcrFlag(tpaImage.getOcrDealedFlag()!=null? tpaImage.getOcrDealedFlag() : 0);
            exist.setPushFlag(tpaImage.getPushFlag()!= null? tpaImage.getPushFlag() : 1);
            imageRepository.save(exist);
        }

    }


    private void saveBenifitPerson(ClaimDetailSyncVO syncVO, Context context) {
        List<ClaimPersonInfo> tpaBenfitList = syncVO.getBenefiList();
        if (tpaBenfitList == null) {
            return;
        }

        Criteria<ClaimStakeholder> criteria = new Criteria();
        criteria.eq(ClaimStakeholder::getRelatedId, syncVO.getClaimNumber());
        criteria.eq(ClaimStakeholder::getPersonType, PersonTypeEnum.BENEFIT.getCode());
        criteria.eq(ClaimStakeholder::getBizIdentityCode, context.getBizIdentityCode());
        //已经存在的受益人
        List<ClaimStakeholder> existList = stakeholderRepository.findByCriteria(criteria,true);
        Map<Long, ClaimStakeholder> tpaIdMapping = new HashMap<>();
        if (PkListUtil.isNotEmpty(existList)) {
            //所有都软删除
            stakeholderRepository.deleteByIds(existList.stream().map(ClaimStakeholder::getId).collect(Collectors.toList()));
            tpaIdMapping = PkListUtil.listToMap(existList.stream().filter(t -> t.getTpaId() != null).collect(Collectors.toList()), ClaimStakeholder::getTpaId);
        }


        for (ClaimPersonInfo tpaPerson : tpaBenfitList) {
            Map<String, String> personHint = new HashMap<>();
            ClaimStakeholder exist = tpaIdMapping.get(tpaPerson.getTpaBenifitId());
            boolean insertTag = false;
            if (exist == null) {
                exist = new ClaimStakeholder();
                exist.setRelatedId(syncVO.getClaimNumber());
                exist.setPersonType(PersonTypeEnum.BENEFIT.getCode());
                exist.setTpaId(tpaPerson.getTpaBenifitId());
                exist.setBizIdentityCode(context.getBizIdentityCode());
                exist.setTenantId(TENANT_ID);
                insertTag = true;
            }
            List<String> clearField = PkListUtil.newArrayList();
            tpaPersonConvertStakHolder(exist, syncVO.getClaimNumber(),
                    BenefitPerson.class, tpaPerson, context, personHint,clearField);
            exist.setBenefitPercentage(tpaPerson.getBenefitPercentage());
            if( insertTag){
                stakeholderRepository.insert(exist);

            }else{
                stakeholderRepository.updateAndClearFields(exist,clearField);

            }
            //保存hint
            saveHint(exist.getId(), HintMsgType.BENEFIT_PERSON, personHint,context);
        }


    }

    private void saveApplyPerson(ClaimDetailSyncVO syncVO, Context context) {
        ClaimPersonInfo applyPersonInfo = syncVO.getApplyPersonInfo();
        if (applyPersonInfo != null) {
            Map<String, String> personHint = new HashMap<>();
            Criteria<ClaimStakeholder> criteria = new Criteria();
            criteria.eq(ClaimStakeholder::getRelatedId, syncVO.getClaimNumber());
            criteria.eq(ClaimStakeholder::getPersonType, PersonTypeEnum.APPLY.getCode());
            criteria.eq(ClaimStakeholder::getBizIdentityCode, context.getBizIdentityCode());
            ClaimStakeholder exist = PkListUtil.first(stakeholderRepository.findByCriteria(criteria,true));
            boolean insertTag = exist == null;
            List<String> clearField = PkListUtil.newArrayList();

            ClaimStakeholder claimStakeholder = convertClaimPersonInfo(syncVO.getClaimNumber(),
                    PersonTypeEnum.APPLY, applyPersonInfo, context, personHint,clearField);
            if( insertTag){
                stakeholderRepository.insert(claimStakeholder);

            }else{
                stakeholderRepository.updateAndClearFields(claimStakeholder,clearField);

            }
            //保存hint
            saveHint(claimStakeholder.getId(), HintMsgType.APPLY_PERSON, personHint,context);
        }
    }

    private void saveOutnsurePerson(ClaimDetailSyncVO syncVO, Context context) {
        ClaimPersonInfo outPersonInfo = syncVO.getOutPersonInfo();
        if (outPersonInfo != null) {
            Map<String, String> personHint = new HashMap<>();
            Criteria<ClaimStakeholder> criteria = new Criteria();
            criteria.eq(ClaimStakeholder::getRelatedId, syncVO.getClaimNumber());
            criteria.eq(ClaimStakeholder::getPersonType, PersonTypeEnum.OUT_INSURE.getCode());
            criteria.eq(ClaimStakeholder::getBizIdentityCode, context.getBizIdentityCode());
            ClaimStakeholder exist = PkListUtil.first(stakeholderRepository.findByCriteria(criteria,true));
            boolean insertTag = exist == null;
            List<String> clearField = PkListUtil.newArrayList();

            ClaimStakeholder claimStakeholder = convertClaimPersonInfo(syncVO.getClaimNumber(),
                    PersonTypeEnum.OUT_INSURE, outPersonInfo, context, personHint,clearField);
            if( insertTag){
                stakeholderRepository.insert(claimStakeholder);

            }else{
                stakeholderRepository.updateAndClearFields(claimStakeholder,clearField);

            }
            //保存hint
            saveHint(claimStakeholder.getId(), HintMsgType.OUT_INSURE_PERSON, personHint,context);
        }
    }

    /**
     * 主被保险人信息
     *
     * @param syncVO
     * @param context
     */
    private void saveMainInsurePerson(ClaimDetailSyncVO syncVO, Context context) {
        ClaimPersonInfo mainInsurePersonInfo = syncVO.getMainPersonInfo();
        if (mainInsurePersonInfo != null) {
            Map<String, String> personHint = new HashMap<>();

            Criteria<ClaimStakeholder> criteria = new Criteria();
            criteria.eq(ClaimStakeholder::getRelatedId, syncVO.getClaimNumber());
            criteria.eq(ClaimStakeholder::getPersonType, PersonTypeEnum.MAIN_INSURE.getCode());
            criteria.eq(ClaimStakeholder::getBizIdentityCode, context.getBizIdentityCode());
            ClaimStakeholder exist = PkListUtil.first(stakeholderRepository.findByCriteria(criteria,true));
            boolean insertTag = exist == null;
            List<String> clearField = PkListUtil.newArrayList();


            ClaimStakeholder claimStakeholder = convertClaimPersonInfo(syncVO.getClaimNumber(),
                    PersonTypeEnum.MAIN_INSURE, mainInsurePersonInfo, context, personHint,clearField);
            if( insertTag){
                stakeholderRepository.insert(claimStakeholder);

            }else{
                stakeholderRepository.updateAndClearFields(claimStakeholder,clearField);

            }
            //保存hint
            saveHint(claimStakeholder.getId(), HintMsgType.MAIN_INSURE_PERSON, personHint,context);
        }
    }

    /**
     * 统一转化接口
     * 领款人要单独写，因为属性不一样
     *
     * @param claimNumber
     * @param tpaPerson
     * @return
     */
    private ClaimStakeholder convertClaimPersonInfo(Long claimNumber,
                                                    PersonTypeEnum personTypeEnum,
                                                    ClaimPersonInfo tpaPerson,
                                                    Context context, Map<String, String> personHint,
                                                    List<String> clearField ) {

        if (tpaPerson == null) {
            return null;
        }
        Class   clz = null;
        if (personTypeEnum == PersonTypeEnum.MAIN_INSURE) {
            clz = MainInsurePerson.class;
        }else if (personTypeEnum == PersonTypeEnum.BENEFIT) {
            clz = BenefitPerson.class;
        }else  if (personTypeEnum == PersonTypeEnum.OUT_INSURE) {
            clz = OutInsurePerson.class;
        }else   if (personTypeEnum == PersonTypeEnum.APPLY) {
            clz = ApplyPerson.class;
        } else {
            throw new RuntimeException("not match personType ");
        }

        Assert.notNull(claimNumber, "claimNumber is null");
        Criteria<ClaimStakeholder> criteria = new Criteria();
        criteria.eq(ClaimStakeholder::getRelatedId, claimNumber);
        criteria.eq(ClaimStakeholder::getPersonType, personTypeEnum.getCode());
        criteria.eq(ClaimStakeholder::getBizIdentityCode,context.getBizIdentityCode());
        ClaimStakeholder exist = PkListUtil.first(stakeholderRepository.findByCriteria(criteria));
        if (exist == null) {
            exist = new ClaimStakeholder();
            exist.setRelatedId(claimNumber);
            exist.setBizIdentityCode(context.getBizIdentityCode());
            exist.setPersonType(personTypeEnum.getCode());
            exist.setTenantId(TENANT_ID);
        }
        tpaPersonConvertStakHolder(exist, claimNumber,
                clz,
                tpaPerson, context, personHint,clearField);
        return exist;
    }

    private void tpaPersonConvertStakHolder(ClaimStakeholder exist, Long claimNumber,
                                            Class clz,
                                            ClaimPersonInfo tpaPerson,
                                            Context context, Map<String, String> personHint,
                                            List<String> clearField ) {
        PersonHead annoClass = (PersonHead) clz.getAnnotation(PersonHead.class);
        if( annoClass == null){
            throw new IllegalArgumentException("not find PersonHead annotation");
        }
        PersonTypeEnum personTypeEnum = annoClass.personType();
        FieldModelDefine fieldModelDefine = annoClass.moduleCode();

        exist.setDeleted(false);
        exist.setName(nullIfEmpty(tpaPerson.getName()));
        if( StringUtils.isNotBlank(tpaPerson.getGender())){
            GenderEnum checkIllgle =       GenderEnum.getEnumByCode(tpaPerson.getGender());
            if( checkIllgle == null){
                throw new RuntimeException("gender is illgle:"+tpaPerson.getGender());
            }
            exist.setGender(tpaPerson.getGender());
        }else{
            exist.setGender("");
        }

        exist.setBirthday(nullIfEmpty(tpaPerson.getBirthday()));
        String identitTypePageField = PersonFieldTransfer.getPersonPageFieldCode(PersonHolderConstants.identityType,
                clz);
        if (StringUtils.isNotBlank(identitTypePageField)) {
            OptionSetDTO identitCodeOption = matchCollectionCodeForSync(tpaPerson.getIdentityTypeCn(),
                    identitTypePageField,
                    context, fieldModelDefine, true, personHint);
            exist.setIdentityType(getOptionSetDTOCode(identitCodeOption));
            ExtraStoreUtil.fillExtraStoreWithOrigin(exist,identitTypePageField, identitCodeOption);
            //证件类型中文
            exist.setIdentityTypeCn(nullIfEmpty(tpaPerson.getIdentityTypeCn()));

        }


        exist.setIdentityNo(tpaPerson.getIdentityNo());
        StringBuffer identityPeriodSb = new StringBuffer();
        if (StringUtils.isNotBlank(tpaPerson.getIdentityStart())) {
            identityPeriodSb.append(tpaPerson.getIdentityStart());
        }
        identityPeriodSb.append(",");
        if (StringUtils.isNotBlank(tpaPerson.getIdentityEnd())) {
            identityPeriodSb.append(tpaPerson.getIdentityEnd());
        }
        exist.setIdentityDatePeriod(identityPeriodSb.toString());
        //从事职业
        String occupationPageField = PersonFieldTransfer.getPersonPageFieldCode(
                PersonHolderConstants.occupation,
                clz);
        if (StringUtils.isNotBlank(occupationPageField)) {
            OptionSetDTO occupationCodeOption = matchCollectionCodeForSync(tpaPerson.getOccupationCn(),
                    occupationPageField,
                    context, fieldModelDefine, true, personHint);
            exist.setOccupation(getOptionSetDTOCode(occupationCodeOption));
            ExtraStoreUtil.fillExtraStoreWithOrigin(exist,occupationPageField, occupationCodeOption);
            exist.setOccupationCn(nullIfEmpty(tpaPerson.getOccupationCn()));
        }

        // 国籍

        String nationalityField = PersonFieldTransfer.getPersonPageFieldCode(
                PersonHolderConstants.nationality,
                clz);
        if (StringUtils.isNotBlank(nationalityField)) {
            OptionSetDTO nationalityCodeOption = matchCollectionCodeForSync(tpaPerson.getNationality(),
                    nationalityField,
                    context, fieldModelDefine, true, personHint);
            exist.setNationality(getOptionSetDTOCode(nationalityCodeOption));
            ExtraStoreUtil.fillExtraStoreWithOrigin(exist,nationalityField, nationalityCodeOption);

            exist.setNationalityCn(nullIfEmpty(tpaPerson.getNationality()));
        }
        exist.setPhone(nullIfEmpty(tpaPerson.getPhone()));
        exist.setContactAddress(nullIfEmpty(tpaPerson.getContactAddress()));
        String contactRegion = matchProviceCityAreaWithChinese(tpaPerson.getContactProvice(),
                tpaPerson.getContactCity(),
                tpaPerson.getContactArea(),3);
        exist.setContactRegion(nullIfEmpty(contactRegion));


        //relationToOutInsureCn 和出险人关系

        String relationToOutInsurePageCode = PersonFieldTransfer.getPersonPageFieldCode(
                PersonHolderConstants.relationToOutInsure,
                clz);

        if (StringUtils.isNotBlank(relationToOutInsurePageCode)) {
            OptionSetDTO relationToOutInsureCodeOption = matchCollectionCodeForSync(tpaPerson.getRelationToOutInsureCn(),
                    relationToOutInsurePageCode,
                    context, fieldModelDefine, true, personHint);
            exist.setRelationToOutInsure(getOptionSetDTOCode(relationToOutInsureCodeOption));
            ExtraStoreUtil.fillExtraStoreWithOrigin(exist,relationToOutInsurePageCode, relationToOutInsureCodeOption);

            exist.setRelationToOutInsureCn(nullIfEmpty(tpaPerson.getRelationToOutInsureCn()));
        }
        //relationToMainInsureCn 和主被保险人关系人关系
        String relationToMainInsurePageCode = PersonFieldTransfer.getPersonPageFieldCode(
                PersonHolderConstants.relationToMainInsure,
                clz);
        if (StringUtils.isNotBlank(relationToMainInsurePageCode)) {
            OptionSetDTO relationToMainInsureCodeOption = matchCollectionCodeForSync(tpaPerson.getRelationToMainInsureCn(),
                    relationToMainInsurePageCode,
                    context, fieldModelDefine, true, personHint);
            exist.setRelationToMainInsure(getOptionSetDTOCode(relationToMainInsureCodeOption));
            ExtraStoreUtil.fillExtraStoreWithOrigin(exist,relationToMainInsurePageCode, relationToMainInsureCodeOption);
            exist.setRelationToMainInsureCn(nullIfEmpty(tpaPerson.getRelationToMainInsureCn()));
        }

        return;
    }

    /**
     * 保存领款人信息
     *
     * @param syncVO
     * @param context
     */
    private void saveCollectPerson(ClaimDetailSyncVO syncVO,  Context context) {
        CollectPersonInfo collectPersonInfo = syncVO.getCollectPersonInfo();
        if (collectPersonInfo == null) {
            return;
        }
        String collectTypeCn = collectPersonInfo.getCollectTypeCn();
        if(StringUtils.isBlank(collectTypeCn)){
            return;
        }
        CollectTypeEnum collectType = CollectTypeEnum.getEnumByTransfer(collectPersonInfo.getCollectTypeCn());
        if (collectType == null) {
            throw new RuntimeException("collectType is not match");
        }





        Map<String, String> hint = new HashMap<>();
        if (collectType == CollectTypeEnum.PERSON) {
            Criteria<ClaimStakeholder> criteria = new Criteria();
            criteria.eq(ClaimStakeholder::getRelatedId, context.getClaim().getId());
            criteria.eq(ClaimStakeholder::getPersonType, PersonTypeEnum.COLLECT.getCode());
            criteria.eq(ClaimStakeholder::getBizIdentityCode, context.getBizIdentityCode());
            ClaimStakeholder exist = PkListUtil.first(stakeholderRepository.findByCriteria(criteria,true));
            boolean insertTag = exist == null;
            if (exist == null) {
                exist = new ClaimStakeholder();
                exist.setDeleted(false);
                exist.setRelatedId(context.getClaim().getId());
                exist.setPersonType(PersonTypeEnum.COLLECT.getCode());
                exist.setBizIdentityCode(context.getBizIdentityCode());
                exist.setTenantId(TENANT_ID);

            }
            List<String> clearField = PkListUtil.newArrayList();

            tpaPersonConvertStakHolder(exist, context.getClaim().getId(),
                    CollectPerson.class,   collectPersonInfo,
                    context, hint,clearField);
            //和受益人关系
            String relationToBenifitPageCode = PersonFieldTransfer.getPersonPageFieldCode(
                    PersonHolderConstants.relationToBenefit,
                    CollectPerson.class);
            if (StringUtils.isNotBlank(relationToBenifitPageCode)) {
                OptionSetDTO relationToBenifitOption = matchCollectionCodeForSync(collectPersonInfo.getRelationToBenifitCn(),
                        relationToBenifitPageCode,
                        context, FieldModelDefine.受益人, true, hint);
                exist.setRelationToBenefit(getOptionSetDTOCode(relationToBenifitOption));
                ExtraStoreUtil.fillExtraStoreWithOrigin(exist,relationToBenifitPageCode, relationToBenifitOption);
                exist.setRelationToBenefitCn(nullIfEmpty(collectPersonInfo.getRelationToBenifitCn()));
            }

            //支付信息
            OptionSetDTO paymentTypeCodeOption = matchCollectionCodeForSync(collectPersonInfo.getPaymentMethodTypeCn(),
                    CollectPersonConstants.personPaymentMethodType,
                    context, FieldModelDefine.领款人信息, true, hint);


            exist.setPaymentMethodType(getOptionSetDTOCode(paymentTypeCodeOption));
            ExtraStoreUtil.fillExtraStoreWithOrigin(exist,CollectPersonConstants.personPaymentMethodType, paymentTypeCodeOption);

            exist.setPaymentMethodTypeCn(nullIfEmpty(collectPersonInfo.getPaymentMethodTypeCn()));

            //对公，对私
            exist.setTransferMethodType(nullIfEmpty(collectPersonInfo.getTransferMethodTypeCn()));
            String  bankRegion  = matchProviceCityAreaWithChinese(collectPersonInfo.getBankProvince(),
                    collectPersonInfo.getBankCity(), null,2);
            exist.setBankRegion(nullIfEmpty(bankRegion));

            //开户行分行
            OptionSetDTO brankBranchCodeOption = matchCollectionCodeForSync(collectPersonInfo.getBankBranchName(),
                    CollectPersonConstants.personBranchCode,  context, FieldModelDefine.领款人信息,
                    true, hint);
            exist.setBranchCode(getOptionSetDTOCode(brankBranchCodeOption));
            ExtraStoreUtil.fillExtraStoreWithOrigin(exist,CollectPersonConstants.personBranchCode, brankBranchCodeOption);
            exist.setBranchCodeCn(nullIfEmpty(collectPersonInfo.getBankBranchName()));
            //开户银行
            OptionSetDTO bankCodeOption = matchCollectionCodeForSync(collectPersonInfo.getBankName(),
                    CollectPersonConstants.personBankCode,
                    context, FieldModelDefine.领款人信息, true, hint);
            exist.setBankCode(getOptionSetDTOCode(bankCodeOption));
            ExtraStoreUtil.fillExtraStoreWithOrigin(exist,CollectPersonConstants.personBankCode, bankCodeOption);
            exist.setBankCodeCn(nullIfEmpty(collectPersonInfo.getBankName()));


            //扩展信息
            Map<String, String> extMap = collectPersonInfo.getExtMap();
            if (extMap != null && extMap.size() > 0) {
                //发票扩展字段
                Map<String, Object> extendFieldMap = mapToExtendMap(FieldModelDefine.领款人信息,context,exist,
                        context.getBizIdentityCode(), context.getClaimNumber(),
                        hint, PkListUtil.asList(),
                        context.getCollectPersonExtMap(), extMap,exist);
                exist.setExtraProperties(extendFieldMap);
            }
            //开户行
            exist.setAccountNo(nullIfEmpty(collectPersonInfo.getAccountNo()));
            exist.setBankAddress(nullIfEmpty(collectPersonInfo.getBankAddress()));
            if( insertTag){
                stakeholderRepository.insert(exist);

            }else{
                stakeholderRepository.updateAndClearFields(exist,clearField);

            }
            saveHint(exist.getId(), HintMsgType.COLLECT_PERSON, hint,context);

        }

        if (collectType == CollectTypeEnum.COMPANY) {
            Criteria<ClaimStakeholder> criteria = new Criteria();
            criteria.eq(ClaimStakeholder::getRelatedId, context.getClaim().getId());
            criteria.eq(ClaimStakeholder::getPersonType, PersonTypeEnum.COLLECT_BUSINESS.getCode());
            criteria.eq(ClaimStakeholder::getBizIdentityCode, context.getBizIdentityCode());
            ClaimStakeholder exist = PkListUtil.first(stakeholderRepository.findByCriteria(criteria,true));
            boolean insertTag = exist == null;
            if (exist == null) {
                exist = new ClaimStakeholder();
                exist.setDeleted(false);
                exist.setRelatedId(context.getClaim().getId());
                exist.setPersonType(PersonTypeEnum.COLLECT_BUSINESS.getCode());
                exist.setBizIdentityCode(context.getBizIdentityCode());
                exist.setTenantId(TENANT_ID);

            }
            List<String> clearField = PkListUtil.newArrayList();


            //做一层转化
            businuessCollectConvertStakHolder(exist, collectPersonInfo, context.getClaim().getId(),
                       collectPersonInfo,
                    context, hint,clearField);

            //扩展信息
            Map<String, String> extMap = collectPersonInfo.getExtMap();
            if (extMap != null && extMap.size() > 0) {
                //发票扩展字段
                Map<String, Object> extendFieldMap = mapToExtendMap(FieldModelDefine.领款企业信息,context,exist,
                        context.getBizIdentityCode(), context.getClaimNumber(),
                        hint, PkListUtil.asList(),
                        context.getCollectBussinessExtMap(), extMap,exist);
                exist.setExtraProperties(extendFieldMap);
            }
            //开户行
            exist.setAccountNo(collectPersonInfo.getAccountNo());
            exist.setBankAddress(collectPersonInfo.getBankAddress());
            if( insertTag){
                stakeholderRepository.insert(exist);

            }else{
                stakeholderRepository.updateAndClearFields(exist,clearField);

            }
            saveHint(exist.getId(), HintMsgType.COLLECT_BUSINESS, hint,context);
        }


    }


    private void businuessCollectConvertStakHolder(ClaimStakeholder exist,
                                                   CollectPersonInfo tpaData, Long claimNumber,
                                                   CollectPersonInfo tpaPerson,
                                                   Context context, Map<String, String> personHint,
                                                   List<String> clearFieldList) {

        tpaPersonConvertStakHolder(exist, context.getClaim().getId(),
                CollectBusiness.class, tpaData,
                context, personHint,clearFieldList);
        //开始处理business那套
        exist.setBusinessName(nullIfEmpty(tpaData.getBusinessName()));
        exist.setBusinessIdentityDisc(nullIfEmpty(tpaData.getBusinessIdentityDisc()));

        //businessIdentityTypeCn
        OptionSetDTO businessIdentityTypeCodeOption = matchCollectionCodeForSync(tpaData.getBusinessIdentityTypeCn(),
                CollectBusinuessConstants.businessIdentityType,
                context, FieldModelDefine.领款企业信息, true, personHint);
        exist.setBusinessIdentityType(getOptionSetDTOCode(businessIdentityTypeCodeOption));
        ExtraStoreUtil.fillExtraStoreWithOrigin(exist,CollectBusinuessConstants.businessIdentityType,
                businessIdentityTypeCodeOption);



        exist.setBusinessIdentityTypeCn(nullIfEmpty(tpaData.getBusinessIdentityTypeCn()));

        exist.setBusinessIdentityNo(nullIfEmpty(tpaData.getBusinessIdentityNo()));

        StringBuffer identityPeriodSb = new StringBuffer();
        if (StringUtils.isNotBlank(tpaPerson.getBusinessIdentityStart())) {
            identityPeriodSb.append(tpaPerson.getBusinessIdentityStart());
        }
        identityPeriodSb.append(",");
        if (StringUtils.isNotBlank(tpaPerson.getBusinessIdentityEnd())) {
            identityPeriodSb.append(tpaPerson.getBusinessIdentityEnd());
        }
        exist.setBusinessIdentityDatePeriod(identityPeriodSb.toString());

        exist.setBusinessPlace(nullIfEmpty(tpaData.getBusinessPlace()));
        exist.setBusinessRange(nullIfEmpty(tpaData.getBusinessRange()));


        //支付信息
        OptionSetDTO paymentTypeCodeOption = matchCollectionCodeForSync(tpaData.getPaymentMethodTypeCn(),
                CollectBusinuessConstants.businessPaymentMethodType,
                context, FieldModelDefine.领款企业信息, true, personHint);

        exist.setPaymentMethodTypeCn(nullIfEmpty(tpaData.getPaymentMethodTypeCn()));
        exist.setPaymentMethodType(getOptionSetDTOCode(paymentTypeCodeOption));
        ExtraStoreUtil.fillExtraStoreWithOrigin(exist,CollectBusinuessConstants.businessPaymentMethodType,paymentTypeCodeOption);

        exist.setTransferMethodType(nullIfEmpty(CollectTypeEnum.COMPANY.getTransferMethod()));
        String bankRegion = matchProviceCityAreaWithChinese(tpaData.getBankProvince(),
                tpaData.getBankCity(), null,2);
        exist.setBankRegion(bankRegion);


        //开户行分行
        OptionSetDTO brankBranchCodeOption = matchCollectionCodeForSync(tpaData.getBankBranchName(),
                CollectBusinuessConstants.businessBranchCode,  context, FieldModelDefine.领款企业信息,
                true, personHint);
        exist.setBranchCode(getOptionSetDTOCode(brankBranchCodeOption));
        ExtraStoreUtil.fillExtraStoreWithOrigin(exist,CollectBusinuessConstants.businessBranchCode,brankBranchCodeOption);
        exist.setBranchCodeCn(nullIfEmpty(tpaData.getBankBranchName()));
        //开户银行
        OptionSetDTO bankCodeOption = matchCollectionCodeForSync(tpaData.getBankName(),
                CollectBusinuessConstants.businessBankCode,
                context, FieldModelDefine.领款企业信息, true, personHint);
        exist.setBankCode(getOptionSetDTOCode(bankCodeOption));
        ExtraStoreUtil.fillExtraStoreWithOrigin(exist,CollectBusinuessConstants.businessBankCode,bankCodeOption);

        exist.setBankCodeCn(nullIfEmpty(tpaData.getBankName()));
    }

    /**
     * 保存发票
     *
     * @param syncVO
     * @param claimConfig
     * @param context     1 发票信息
     *                    2 项目信息
     *                    3 费用明细信息
     */
    private void saveInvoice(ClaimDetailSyncVO syncVO,   Context context) {
        Claim claim = context.getClaim();
        //删除原来的发票,包括所有的已删除的
        List<ClaimInvoice> existList = loadAllInvoiceIgnoreDeleted(claim.getId(),context.getBizIdentityCode());

        List<InvoiceInfo> invoiceInfoList = syncVO.getInvoiceList();
        for (ClaimInvoice invoice : existList) {
            if (invoice.getDeleted()) {
                continue;
            }
            ClaimInvoice updatedto = new ClaimInvoice();
            updatedto.setId(invoice.getId());
            updatedto.setDeleted(true);
            invoiceRepository.update(updatedto);
        }
        if (PkListUtil.isEmpty(invoiceInfoList)) {
            return;
        }
        Map<String, ClaimInvoice> invoiceExistMap = PkListUtil.listToMap(existList, ClaimInvoice::getInvoiceUuid);
        for (InvoiceInfo invoiceRemote : invoiceInfoList) {
            ClaimInvoice exist = invoiceExistMap.get(invoiceRemote.getInvoiceUuid());
            boolean insertTag = false;
            if (exist == null) {
                //add
                exist = new ClaimInvoice();
                //雪花算法
                exist.setId(IdUtil.getSnowflake(SnowUtil.getWorkId(), 1).nextId());
                exist.setInvoiceUuid(invoiceRemote.getInvoiceUuid());
                exist.setRelatedId(claim.getId());
                exist.setBizIdentityCode(context.getBizIdentityCode());
                exist.setTenantId(TENANT_ID);

                insertTag = true;
            }
            exist.setDeleted(false);
            List<String> clearList = Lists.newArrayList();
            Map<String, String> invoiceHint = new HashMap<>();
            invoiceConvert.convertToInvoice(invoiceRemote, exist, insertTag, clearList, context, invoiceHint);

            List<OnSaveInvoiceHook>  invoiceHookList =  claimHookUtil.getOnSaveInvoiceHookList(claim.getBizIdentityCode());
            for( OnSaveInvoiceHook hook: invoiceHookList){
                hook.doEvent(exist);
            }

           // yongChengUpdateRule.onUpdateInvoice(exist);
            if( insertTag){

                ObjInvoke.getObjBigDemical(exist);
                invoiceRepository.insert(exist);

            }else{
                invoiceRepository.updateAndClearFields(exist,clearList);
            }

            //保存hint
            saveHint(exist.getId(), HintMsgType.CLAIM_INVOICE, invoiceHint,context);

            Integer inputCheckType = claim.getCfgBizInputType();
            CfgBizInputTypeEnum inputType =   CfgBizInputTypeEnum.getByCode(inputCheckType);
            if(inputCheckType== null || inputType == CfgBizInputTypeEnum.INVOICE_AND_PROJECT){
                //save project 保存项目信息,删除+插入
                saveProject(exist, invoiceRemote, context);
                //save item cost 保存费用信息
                saveItemCosts(exist, invoiceRemote, context);
            }

        }

    }

    private void saveItemCosts(ClaimInvoice dbInvoice,
                               InvoiceInfo invoiceInfoRemote,
                               Context context) {

        //删除现有的
        Criteria<InvoiceProjectItem> criteria = new Criteria();
        criteria.eq(InvoiceProjectItem::getRelatedId, dbInvoice.getId());
        criteria.eq(InvoiceProjectItem::getBizIdentityCode, context.getBizIdentityCode());
        List<InvoiceProjectItem> existList = itemRepository.findByCriteria(criteria, true);
        if (PkListUtil.isEmpty(existList)) {
            itemRepository.deleteByIds(existList.stream().map(InvoiceProjectItem::getId)
                    .collect(Collectors.toList()));
        }
        List<CostItemInfo> remoteCostList = invoiceInfoRemote.getCostItemInfoList();
        if (remoteCostList == null) {
            return;
        }
        for(CostItemInfo costItemInfo : remoteCostList){
            if(StringUtils.isBlank(costItemInfo.getCostUuid())){
                throw new RuntimeException("cost uuid is empty:"+costItemInfo.getDrugName());
            }
        }

        Map<String, InvoiceProjectItem> existItemNameap =
                PkListUtil.listToMap(existList.stream().filter(t->t.getItemUuid()!= null).collect(Collectors.toList()),
                        InvoiceProjectItem::getItemUuid);

    /*    Criteria<InvoiceProject> criteriaProject = new Criteria();
        criteriaProject.eq(InvoiceProject::getRelatedId, dbInvoice.getId());
        List<InvoiceProject> existProjectList = projectRepository.findByCriteria(criteriaProject);
        */
        //新增
        // TODO: 2025/4/30
        for (CostItemInfo remoteCost : remoteCostList) {
            Map<String, String> costHint = new HashMap<>();
            List<String> clearField = Lists.newArrayList();
            InvoiceProjectItem exist = existItemNameap.get(remoteCost.getCostUuid());
            boolean insertTag = exist == null;

            if (exist == null) {
                exist = new InvoiceProjectItem();
                exist.setItemUuid(remoteCost.getCostUuid());
                //只在创建时间
                exist.setRelatedId(dbInvoice.getId());
                exist.setBizIdentityCode(context.getBizIdentityCode());
                exist.setTenantId(TENANT_ID);

            }

            exist.setDeleted(false);
            OptionSetDTO projectCodeOption = matchCollectionCodeForSync(remoteCost.getProjectName(),
                    CostItemFieldConstants.projectCode,
                    context, FieldModelDefine.发票费用明细, true, costHint);
            exist.setRelatedProjectCode(getOptionSetDTOCode(projectCodeOption));
            ExtraStoreUtil.fillExtraStoreWithOrigin(exist,CostItemFieldConstants.projectCode,projectCodeOption);

            exist.setRelatedProjectName(nullIfEmpty(remoteCost.getProjectName()));
            exist.setItemName(nullIfEmpty(remoteCost.getDrugName()));
            if( remoteCost.getItemUnit() != null){
                exist.setCount(remoteCost.getItemUnit());

            }else {
                clearField.add("count");
            }
            //medicalTypeCn 医保类型中文

            MedicalCostTypeEnum medicalCostTypeEnum = MedicalCostTypeEnum.getByDesc(remoteCost.getMedicalTypeCn());
            if(medicalCostTypeEnum != null){
                exist.setMedicalType(medicalCostTypeEnum.getCode());
            }else{
                costHint.put(CostItemFieldConstants.medicalType, remoteCost.getMedicalTypeCn());
                exist.setMedicalType("");

            }
            if( remoteCost.getSelfPayPercent() != null){
                exist.setChargingPercentage(remoteCost.getSelfPayPercent());

            }else {
                clearField.add("charging_percentage");
            }
            if( remoteCost.getUnitPrice() != null){
                exist.setPrice(remoteCost.getUnitPrice());
            }else{
                clearField.add("price");
            }
            if( remoteCost.getPayAmount() != null){
                exist.setChargingAmount(remoteCost.getPayAmount());
            }else {
                clearField.add("charging_amount");
            }
            if(remoteCost.getOccurAmount() != null){
                exist.setItemTotalAmount(remoteCost.getOccurAmount());

            }else{
                clearField.add("item_total_amount");
            }
           //dosageFormCn
            OptionSetDTO dosageFormCodeOption = matchCollectionCodeForSync(remoteCost.getDosageFormCn(),
                    CostItemFieldConstants.dosageForm,
                    context, FieldModelDefine.发票费用明细, true, costHint);
            exist.setDosageForm(getOptionSetDTOCode(dosageFormCodeOption));
            ExtraStoreUtil.fillExtraStoreWithOrigin(exist,CostItemFieldConstants.dosageForm,dosageFormCodeOption);

            exist.setDosageFormCn(nullIfEmpty(remoteCost.getDosageFormCn()));
            Map<String, String> extMap = remoteCost.getExtMap();
            if (extMap != null && extMap.size() > 0) {
                //发票扩展字段
                Map<String, Object> extendFieldMap = mapToExtendMap(FieldModelDefine.发票费用明细,context,exist,
                        context.getBizIdentityCode(), context.getClaimNumber(),
                        costHint, clearField,
                        context.getProjectItemExtMap(), extMap,exist);
                exist.setExtraProperties(extendFieldMap);
            }
            if(insertTag){
                ObjInvoke.getObjBigDemical(exist);
                itemRepository.insert(exist);

            }else {
                itemRepository.updateAndClearFields(exist, clearField);
            }
           //保存hint
            saveHint(exist.getId(), HintMsgType.INVOICE_PROJECT_ITEM, costHint,context);
        }
    }

    /**
     * 存储项目信息
     *
     * @param dbInvoice
     * @param invoiceInfoRemote
     */
    private void saveProject(ClaimInvoice dbInvoice,
                             InvoiceInfo invoiceInfoRemote, Context context) {


        //删除现有的
       /* Criteria<InvoiceProject> criteria = new Criteria();
        criteria.eq(InvoiceProject::getRelatedId, dbInvoice.getId());
        List<InvoiceProject> existList = projectRepository.findByCriteria(criteria, true);
        if (PkListUtil.isEmpty(existList)) {
            projectRepository.deleteByIds(existList.stream().map(InvoiceProject::getId)
                    .collect(Collectors.toList()));
        }
        List<ProjectInfo> projectInfoList = invoiceInfoRemote.getProjectInfoList();
        if (projectInfoList == null) {
            return;
        }

        Map<String, InvoiceProject> existProjectNameMap = PkListUtil.listToMap(existList, InvoiceProject::getProjectName);

        //插入
        for (ProjectInfo projectInfoRemote : projectInfoList) {
            Map<String, String> projectHint = new HashMap<>();
            List<String> clearField = Lists.newArrayList();
            //插入数据

            InvoiceProject project = existProjectNameMap.get(projectInfoRemote.getProjectName());
            boolean insertTag = project == null;

            if (project == null) {
                //防止脏数据
                project = new InvoiceProject();
                project.setRelatedId(dbInvoice.getId());
                project.setBizIdentityCode(context.getBizIdentityCode());
                project.setTenantId(TENANT_ID);


            }

            String projectCode = matchCollectionCode(projectInfoRemote.getProjectName(),
                    ProjectConstants.projectCode,
                    context, FieldModelDefine.发票项目信息, true, projectHint);
            project.setProjectCode(nullIfEmpty(projectCode));
            project.setProjectName(nullIfEmpty(projectInfoRemote.getProjectName()));

            project.setInvoiceAmount(projectInfoRemote.getAmount());
            //allSelfPayAmount
            if(projectInfoRemote.getAllSelfPayAmount()!= null){
                project.setProjectSelfPayAmount(projectInfoRemote.getAllSelfPayAmount());

            }else{
                clearField.add("project_self_pay_amount");
            }
             //partSelfPayAmount
            if(projectInfoRemote.getPartSelfPayAmount()!= null){
                project.setProjectPartSelfPayAmount(projectInfoRemote.getPartSelfPayAmount());

            }else {
                clearField.add("project_part_self_pay_amount");
            }
            //thirdPayAmount
            if( projectInfoRemote.getThirdPayAmount() != null){
                project.setProjectThirdPartyPayAmount(projectInfoRemote.getThirdPayAmount());

            }else{
                clearField.add("project_third_party_pay_amount");
            }
            //reasonableAmount
            if(projectInfoRemote.getReasonableAmount()!= null){
                project.setProjectReasonableAmount(projectInfoRemote.getReasonableAmount());
            }else{
                clearField.add("project_reasonable_amount");
            }

            //unReasonableAmount
            if(projectInfoRemote.getUnReasonableAmount()!= null){
                project.setProjectUnreasonableAmount(projectInfoRemote.getUnReasonableAmount());

            }else{
                clearField.add("project_unreasonable_amount");
            }
            project.setDeleted(false);
            //扩展字段
            Map<String, String> extMap = projectInfoRemote.getExtMap();
            if (extMap != null && extMap.size() > 0) {
                //发票扩展字段
                Map<String, Object> extendFieldMap = mapToExtendMap(FieldModelDefine.发票项目信息,
                        context.getBizIdentityCode(), projectHint, clearField,
                        context.getProjectExtMap(), extMap);
                project.setExtraProperties(extendFieldMap);
            }

            if( insertTag){
                projectRepository.insert(project);

            }else{
                projectRepository.updateAndClearFields(project,clearField);

            }


            //保存hint
            saveHint(project.getId(), HintMsgType.INVOICE_PROJECT, projectHint,context);
        }*/

    }

    /**
     * 查询一个赔案的所有发票（忽略删除标记）
     *
     * @param claimId
     * @return
     */
    private List<ClaimInvoice> loadAllInvoiceIgnoreDeleted(Long claimId,String bizIdentityCode) {
        Criteria<ClaimInvoice> criteria = new Criteria();
        criteria.eq(ClaimInvoice::getRelatedId, claimId);
        criteria.eq(ClaimInvoice::getBizIdentityCode, bizIdentityCode);
        List<ClaimInvoice> invoiceList = invoiceRepository.findByCriteria(criteria, true);
        return invoiceList;
    }

    /**
     * 保存赔案主体
     *
     * @param syncVO
     * @param context
     */
    private void saveClaim(ClaimDetailSyncVO syncVO, ClaimConfig claimConfig, Context context) {
        //忽略删除标记，因为claimNumber 作为主键
        Claim existClaim = claimRepository.findById(syncVO.getClaimNumber());
        boolean insertTag = false;
        if (existClaim == null) {
            //一般第一次
            existClaim = new Claim();
            existClaim.setBizIdentityCode(context.getBizIdentityCode());
            existClaim.setId(syncVO.getClaimNumber());
            existClaim.setTenantId(TENANT_ID);
            existClaim.setClaimNo(syncVO.getClaimNumber().toString());
            insertTag = true;
        }
        //一些字段需要设置成null
        List<String> clearField = Lists.newArrayList();
        //转化成claim，方便更新
        claimConvert.convertToClaim(syncVO, existClaim, claimConfig, insertTag, clearField, context);
        existClaim.setDeleted(false);
        if (insertTag) {
            //新增
            claimRepository.insert(existClaim);
        } else {
            //修改
            claimRepository.updateAndClearFields(existClaim, clearField);
        }
        //更新hint
        saveHint(existClaim.getId(), HintMsgType.CLAIM_DETAIL, context.getHintClaimDetail(),context);
        //需要重新加载下，到上下文
        Claim contextVo = claimRepository.findById(syncVO.getClaimNumber());
        Assert.notNull(contextVo, "赔案数据插入失败");
        context.setClaim(contextVo);
    }


    /**
     *
     * @param policyNo   保单号
     * @param insureCompanyName  投保公司
     * @param branchInsuranceCompanyName    保险分公司
     * @param insureanceCompanyName   保险公司
     * @return
     */
    public String calBizIdentity(String policyNo,String insureCompanyName,
                                  String branchInsuranceCompanyName,String insureanceCompanyName) {
        PageBizIdentityVO calVo =    calBizIdentityVO(policyNo,insureCompanyName,branchInsuranceCompanyName,insureanceCompanyName);
        if(calVo== null){
            throw new RuntimeException("无法匹配业务主体");
        }
        return  calVo.getBizCode();
    }






    public PageBizIdentityVO calBizIdentityVO(String policyNo,String insureCompanyName,
                                 String branchInsuranceCompanyName,String insuranceCompanyName) {
        Result<List<PageBizIdentityVO>> remoteRs =  pageModelConfigFeign.getAllBizidentity();
        if(!remoteRs.getSuccess()){
            throw new RuntimeException("远程获取业务标识失败");
        }
        log.info("calBizIdentityVO,param:policyNo:{},insureCompanyName:{},branchInsuranceCompanyName:{},insuranceCompanyName:{}",policyNo,insureCompanyName,branchInsuranceCompanyName,insuranceCompanyName);
        List<PageBizIdentityVO> allList =   remoteRs.getData();
        List<PageBizIdentityVO> policyBizList = allList.stream().filter(t->t.getBizType() == 4).collect(Collectors.toList());
        List<PageBizIdentityVO> insuranceCompanyBizList = allList.stream().filter(t->t.getBizType() == 1).collect(Collectors.toList());
        List<PageBizIdentityVO> branchBizList = allList.stream().filter(t->t.getBizType() == 2).collect(Collectors.toList());
        List<PageBizIdentityVO> toubaoBizList = allList.stream().filter(t->t.getBizType() == 3).collect(Collectors.toList());

        for(PageBizIdentityVO identityVO : policyBizList){
            if(identityVO.getBizName().equals(policyNo)){
                log.info("calBizIdentityVO,return policyResult:{}",policyNo);
                return identityVO ;
            }
        }

        for(PageBizIdentityVO identityVO : toubaoBizList){
            if(identityVO.getBizName().equals(insureCompanyName)){
                log.info("calBizIdentityVO,return toubaoResult:{}",insureCompanyName);

                return identityVO;
            }
        }

        for(PageBizIdentityVO identityVO : branchBizList){
            if(identityVO.getBizName().equals(branchInsuranceCompanyName)){
                log.info("calBizIdentityVO,return branchResultResult:{}",branchInsuranceCompanyName);

                return identityVO;
            }
        }

        for(PageBizIdentityVO identityVO : insuranceCompanyBizList){
            if(identityVO.getBizName().equals(insuranceCompanyName)){
                log.info("calBizIdentityVO,return insuranceCompanyResult:{}",insuranceCompanyName);
                return identityVO;
            }

            // 处理一个字符串比另一个多一个"新"前缀的情况
            if (identityVO.getBizName().startsWith("新") && identityVO.getBizName().substring(1).equals(insuranceCompanyName)) {
                log.info("calBizIdentityVO,return insuranceCompanyResult:{}",insuranceCompanyName);
                return identityVO;
            }

            if (insuranceCompanyName.startsWith("新") && insuranceCompanyName.substring(1).equals(identityVO.getBizName())) {
                log.info("calBizIdentityVO,return insuranceCompanyResult:{}",insuranceCompanyName);
                return identityVO;
            }

        }
        log.info("calBizIdentityVO,return null");
        return  null;
    }
}
