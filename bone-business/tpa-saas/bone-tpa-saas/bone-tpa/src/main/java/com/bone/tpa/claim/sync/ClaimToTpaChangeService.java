package com.bone.tpa.claim.sync;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.enums.FieldModelDefine;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.api.enums.CfgBizInputTypeEnum;
import com.bone.tpa.api.enums.CollectTypeEnum;
import com.bone.tpa.api.enums.MedicalCostTypeEnum;
import com.bone.tpa.api.enums.YesOrNoEnum;
import com.bone.tpa.api.vo.*;
import com.bone.tpa.claim.application.response.*;
import com.bone.tpa.claim.application.transfer.PersonFieldTransfer;
import com.bone.tpa.claim.application.transfer.PersonHead;
import com.bone.tpa.claim.application.transfer.PersonHolderConstants;
import com.bone.tpa.claim.sync.Constant.CollectBusinuessConstants;
import com.bone.tpa.claim.sync.Constant.CollectPersonConstants;
import com.bone.tpa.claim.sync.Constant.CostItemFieldConstants;
import com.bone.tpa.claim.domain.ext.strategy.YongChengUpdateRule;
import com.bone.tpa.claim.sync.convert.SyncClaimConvert;
import com.bone.tpa.claim.sync.convert.SyncInvoiceConvert;
import com.bone.tpa.core.util.ExtraStoreUtil;
import com.bone.tpa.facade.vo.ColletionBindVO;
import com.bone.tpa.hook.ClaimHookUtil;
import com.bone.tpa.hook.inter.SyncToTpaAfterDealHook;
import com.bone.tpa.sdk.claim.enums.PersonTypeEnum;
import com.bone.tpa.sdk.claim.model.*;
import com.bone.tpa.sdk.util.ObjInvoke;
import com.bone.tpa.sdk.util.OptionSetObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 返回给tpa的数据封装
 */
@Service
public class ClaimToTpaChangeService  extends SyncBaseTool {
    @Autowired
    protected SyncClaimConvert claimConvert;
    @Autowired
    protected SyncInvoiceConvert invoiceConvert;
    @Autowired
    private YongChengUpdateRule yongChengUpdateRule;


    @Autowired
    private ClaimHookUtil claimHookUtil;

    public ClaimDetailSyncVO toSyncVo(Long claimNumber){
        Claim claim =  claimRepository.findById(claimNumber);
        Context context = new Context();
        String bizIdentityCode = claim.getBizIdentityCode();
        //设置扩展字段到上下文，后面使用
        context.setClaim(claim);
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
        context.setBizIdentityCode(bizIdentityCode);
        Map<String, ColletionBindVO>  allCollectionBind=   loadCollectionBind(bizIdentityCode,PkListUtil.newArrayList());
        context.setAllOptionSetMap(allCollectionBind);

        ClaimDetailSyncVO syncVO = new ClaimDetailSyncVO ();
        //填充赔案
        claimConvert.convertToSyncVo( syncVO,claim, context);
        //发票
        convertInvoice(syncVO, claim, context);

        //主被保险人
        convertMainInsurePerson(syncVO, claim, context);
        //出险人
        convertOutInsurePerson(syncVO, claim, context);

        //申请人
        convertApplyPerson(syncVO,claim,context);
        //受益人
        convertBenifitPersonList(syncVO,claim,context);
        //领款人
        convertCollectInfo(syncVO,claim,context);
        //影像
        convertImageList(syncVO,claim,context);
        //影像绑定
        convertImageRelation(syncVO,claim,context);

         List<SyncToTpaAfterDealHook> hookList =   claimHookUtil.getSyncToTpaAfterDealHookList(bizIdentityCode);
        for(SyncToTpaAfterDealHook hook : hookList){
            hook.doEvent(syncVO);
        }
       // yongChengUpdateRule.filterDataWhileToTpa(syncVO);


        return syncVO;
    }


    /**
     * 发票查重接口专用，先同步发票过去
     *
     * @param claimNumber
     * @return
     */
    public ClaimDetailSyncVO toSyncVoInvoiceOnly(Long claimNumber){
        Claim claim =  claimRepository.findById(claimNumber);
        Context context = new Context();
        String bizIdentityCode = claim.getBizIdentityCode();
        //设置扩展字段到上下文，后面使用
        context.setInvoiceExtMap(getExtendConfig(bizIdentityCode, FieldModelDefine.发票));

        ClaimDetailSyncVO syncVO = new ClaimDetailSyncVO ();
        //填充赔案
        claimConvert.convertToSyncVo( syncVO,claim, context);
        //发票
        convertInvoice(syncVO, claim, context);
        return syncVO;
    }


    private void convertImageRelation(ClaimDetailSyncVO syncVO , Claim claim, Context context){
        Criteria<InvoiceImageRelation>  bindCriteria = new Criteria<>();
        bindCriteria.eq(InvoiceImageRelation::getClaimId, claim.getId());
        List<InvoiceImageRelation> relationList =  imageRelationRepository.findByCriteria(bindCriteria);
        List<ImageBindInvoice> imageBindList = PkListUtil.newArrayList();

        Criteria<ClaimInvoice> invoiceCriteria = new Criteria<>();
        invoiceCriteria.eq(ClaimInvoice::getRelatedId, claim.getId());
        List<ClaimInvoice> invoiceList = invoiceRepository.findByCriteria(invoiceCriteria);
        //拿来做校验
        List<String> invoiceUuidList = invoiceList.stream().map(ClaimInvoice::getInvoiceUuid).collect(Collectors.toList());

        Criteria<ClaimImage>  imageCriteria = new Criteria<>();
        imageCriteria.eq(ClaimImage::getRelatedId, claim.getId());
        List<ClaimImage>  saasImageList = imageRepository.findByCriteria(imageCriteria) ;
        List<String> imageUuidList = saasImageList.stream().map(ClaimImage::getImageDetailId).collect(Collectors.toList());

        syncVO.setImageBindList(imageBindList);
        for(InvoiceImageRelation saasBind : relationList){
            if( !invoiceUuidList.contains(saasBind.getInvoiceUuid())){
                continue;
            }
            if(!imageUuidList.contains(saasBind.getImageDetailId())){
                continue;
            }
            ImageBindInvoice imageBind = new ImageBindInvoice();
            imageBind.setId(saasBind.getId());
            imageBind.setInvoiceUuid(saasBind.getInvoiceUuid());
            imageBind.setImageUuid(saasBind.getImageDetailId());
            imageBindList.add(imageBind);

        }
    }

    /**
     * 影像件转化
     * @param syncVO
     * @param claim
     * @param context
     */
    private void convertImageList(ClaimDetailSyncVO syncVO , Claim claim, Context context){
        Criteria<ClaimImage>  imageCriteria = new Criteria<>();
        imageCriteria.eq(ClaimImage::getRelatedId, claim.getId());
        List<ClaimImage>  saasImageList = imageRepository.findByCriteria(imageCriteria) ;
        List<ImageInfo>  tpaImageList = PkListUtil.newArrayList();
        syncVO.setImageList(tpaImageList);
        for(ClaimImage saasImage : saasImageList){
            ImageInfo tpaImage  = new ImageInfo();
            tpaImageList.add(tpaImage);
            tpaImage.setOcrDealedFlag(saasImage.getOcrFlag()!= null?saasImage.getOcrFlag(): YesOrNoEnum.NO.getCode());
            tpaImage.setSource(saasImage.getSourceSystem()!=null?saasImage.getSourceSystem(): 1);
            tpaImage.setImageUuid(saasImage.getImageDetailId());
            tpaImage.setImageName(saasImage.getImageName());
            tpaImage.setImageType(saasImage.getImageType());
            tpaImage.setImagePath(saasImage.getImagePath());
            tpaImage.setImageIndex(saasImage.getImageIndex());
            tpaImage.setImagePkType(saasImage.getImagePkType());
            if( tpaImage .getImagePkType() == null){
                tpaImage.setImagePkType("1");
            }
            tpaImage.setPushFlag(saasImage.getPushFlag()!=null?saasImage.getPushFlag(): YesOrNoEnum.NO.getCode());
        }

    }

    private void convertCollectInfo(ClaimDetailSyncVO syncVO , Claim claim, Context context){
        CollectTypeEnum collectTypeEnum =    CollectTypeEnum.getEnumByCode(claim.getCollectType());
        if( collectTypeEnum == null){
            return;
        }
        if( collectTypeEnum == CollectTypeEnum.PERSON){
            fillCollectPerson(syncVO,claim,context,collectTypeEnum);
        }
        if( collectTypeEnum == CollectTypeEnum.COMPANY){
            fillCollectBusiness(syncVO,claim,context,collectTypeEnum);
        }
    }

    /**
     *
     * @param syncVO
     * @param claim
     * @param context
     * @param collectTypeEnum
     */
    private void fillCollectBusiness(ClaimDetailSyncVO syncVO , Claim claim, Context context,
                                   CollectTypeEnum collectTypeEnum){
        Criteria<ClaimStakeholder> personCriteria = new Criteria<>();
        personCriteria.eq(ClaimStakeholder::getRelatedId, claim.getId());
        personCriteria.eq(ClaimStakeholder::getPersonType, PersonTypeEnum.COLLECT_BUSINESS.getCode());
        personCriteria.eq(ClaimStakeholder::getBizIdentityCode,claim.getBizIdentityCode());
        ClaimStakeholder  collectPerosn = PkListUtil.first(stakeholderRepository.findByCriteria(personCriteria));
        if( collectPerosn == null){
            return;
        }
        CollectPersonInfo tpaPerson = new CollectPersonInfo();
        tpaPerson.setCollectTypeCn(collectTypeEnum.getTransferMethod());
        convertToTpaPerson(collectPerosn, CollectPerson.class,tpaPerson,context,context.getCollectPersonExtMap());
        //填充扩展属性一次

        //赔付方式中文

        // CollectBusinuessConstants.businessPaymentMethodType
        OptionSetObject paymentMethodTypeExtraStore =  ExtraStoreUtil.getOptionSetValue(
                CollectBusinuessConstants.businessPaymentMethodType,
                collectPerosn.getExtraStore());
        if( paymentMethodTypeExtraStore != null){
            tpaPerson.setPaymentMethodTypeCn(paymentMethodTypeExtraStore.getName());
        }else{
            tpaPerson.setPaymentMethodTypeCn(collectPerosn.getPaymentMethodTypeCn());
        }

        tpaPerson.setTransferMethodTypeCn(CollectTypeEnum.COMPANY.getTransferMethod());

        List<String> bankRegionList =  getRegionCn(collectPerosn.getBankRegion());
        tpaPerson.setBankProvince(bankRegionList.get(0));
        tpaPerson.setBankCity(bankRegionList.get(1));

        OptionSetObject bankCodeStore =  ExtraStoreUtil.getOptionSetValue(CollectBusinuessConstants.businessBankCode,
                collectPerosn.getExtraStore());
        if( bankCodeStore != null){
            tpaPerson.setBankName(bankCodeStore.getName());

        }else{
            tpaPerson.setBankName(collectPerosn.getBankCodeCn());
        }

       // tpaPerson.setBankName(collectPerosn.getBankCodeCn());
        OptionSetObject bankBranchCodeStore =  ExtraStoreUtil.getOptionSetValue(CollectBusinuessConstants.businessBranchCode,
                collectPerosn.getExtraStore());
        if( bankBranchCodeStore != null){
            tpaPerson.setBankBranchName(bankBranchCodeStore.getName());

        }else{
            tpaPerson.setBankBranchName(collectPerosn.getBranchCodeCn());
        }
       // tpaPerson.setBankBranchName(collectPerosn.getBranchCodeCn());
        tpaPerson.setAccountNo(collectPerosn.getAccountNo());
        tpaPerson.setBankAddress(collectPerosn.getBankAddress());
        //bus 的字段
        tpaPerson.setBusinessName(collectPerosn.getBusinessName());
        tpaPerson.setBusinessIdentityDisc(collectPerosn.getBusinessIdentityDisc());


        OptionSetObject businessIdentityTypeExtraStore =  ExtraStoreUtil.getOptionSetValue(CollectBusinuessConstants.businessIdentityType,
                collectPerosn.getExtraStore());
        if( businessIdentityTypeExtraStore != null){
            tpaPerson.setBusinessIdentityTypeCn(businessIdentityTypeExtraStore.getName());
        }else{
            tpaPerson.setBusinessIdentityTypeCn(collectPerosn.getBusinessIdentityTypeCn());
        }


        //tpaPerson.setBusinessIdentityTypeCn(collectPerosn.getBusinessIdentityTypeCn());

        tpaPerson.setBusinessIdentityNo(collectPerosn.getBusinessIdentityNo());
        String businessDatePeriod = collectPerosn.getBusinessIdentityDatePeriod();
        if( businessDatePeriod == null || businessDatePeriod.isBlank()){
            businessDatePeriod = ",";
        }
        String[] businessDatePeriodArr = businessDatePeriod.split(",");
        // 确保数组有足够的长度
        if (businessDatePeriodArr.length < 2) {
            // 处理不足的情况，例如创建新数组
            String[] newArr = new String[2];
            if (businessDatePeriodArr.length > 0) {
                newArr[0] = businessDatePeriodArr[0];
            }
            if (businessDatePeriodArr.length > 1) {
                newArr[1] = businessDatePeriodArr[1];
            }
            businessDatePeriodArr = newArr;
        }

        tpaPerson.setBusinessIdentityStart(
                businessDatePeriodArr[0] != null ? businessDatePeriodArr[0] : "");
        tpaPerson.setBusinessIdentityEnd(
                businessDatePeriodArr[1] != null ? businessDatePeriodArr[1] : "");

        tpaPerson.setBusinessRange(collectPerosn.getBusinessRange());
        tpaPerson.setBusinessPlace(collectPerosn.getBusinessPlace());



        Map<String,String> returnExtMap = extendMapToTpaMap(collectPerosn,claim.getBizIdentityCode(),
                FieldModelDefine.领款企业信息 ,context.getCollectBussinessExtMap(),
                collectPerosn.getExtraProperties() );
        tpaPerson.setExtMap(returnExtMap);


        syncVO.setCollectPersonInfo(tpaPerson);
    }

    /**
     * 领款人
     * @param syncVO
     * @param claim
     * @param context
     */
    private void fillCollectPerson(ClaimDetailSyncVO syncVO , Claim claim, Context context,
                                   CollectTypeEnum collectTypeEnum){
        Criteria<ClaimStakeholder> personCriteria = new Criteria<>();
        personCriteria.eq(ClaimStakeholder::getRelatedId, claim.getId());
        personCriteria.eq(ClaimStakeholder::getPersonType, PersonTypeEnum.COLLECT.getCode());
        personCriteria.eq(ClaimStakeholder::getBizIdentityCode,claim.getBizIdentityCode());
        ClaimStakeholder  collectPerosn = PkListUtil.first(stakeholderRepository.findByCriteria(personCriteria));
        if( collectPerosn == null){
            return;
        }
        CollectPersonInfo tpaPerson = new CollectPersonInfo();
        tpaPerson.setCollectTypeCn(collectTypeEnum.getTransferMethod());

        convertToTpaPerson(collectPerosn, CollectPerson.class,tpaPerson,context,context.getCollectPersonExtMap());
        //tpaPerson.setRelationToBenifitCn(collectPerosn.getRelationToBenefitCn());

        //领款人和受益人关系
        String relationToBenifitPageCode = PersonFieldTransfer.getPersonPageFieldCode(
                PersonHolderConstants.relationToBenefit,
                CollectPerson.class);

        OptionSetObject relationToBenifitExtraStore =  ExtraStoreUtil.getOptionSetValue(relationToBenifitPageCode,collectPerosn.getExtraStore());
        if( relationToBenifitExtraStore != null){
            tpaPerson.setRelationToBenifitCn(relationToBenifitExtraStore.getName());
        }else{
            tpaPerson.setRelationToBenifitCn(collectPerosn.getRelationToBenefitCn());
        }

        OptionSetObject paymentMethodTypeExtraStore =  ExtraStoreUtil.getOptionSetValue(
                CollectPersonConstants.personPaymentMethodType,
                collectPerosn.getExtraStore());
        if( paymentMethodTypeExtraStore != null){
            tpaPerson.setPaymentMethodTypeCn(paymentMethodTypeExtraStore.getName());
        }else{
            tpaPerson.setPaymentMethodTypeCn(collectPerosn.getPaymentMethodTypeCn());
        }
        //对公，对私，走枚举
        tpaPerson.setTransferMethodTypeCn(collectPerosn.getTransferMethodType());

        List<String> bankRegionList =  getRegionCn(collectPerosn.getBankRegion());
        tpaPerson.setBankProvince(bankRegionList.get(0));
        tpaPerson.setBankCity(bankRegionList.get(1));

        //CollectPersonConstants.personBankCode
        OptionSetObject bankNameStore =  ExtraStoreUtil.getOptionSetValue(CollectPersonConstants.personBankCode,
                collectPerosn.getExtraStore());
        if( bankNameStore != null){
            tpaPerson.setBankName(bankNameStore.getName());
        }else{
            tpaPerson.setBankName(collectPerosn.getBankCodeCn());
        }

       // tpaPerson.setBankName(collectPerosn.getBankCodeCn());

        OptionSetObject bankBranchNameStore =  ExtraStoreUtil.getOptionSetValue(CollectPersonConstants.personBranchCode,
                collectPerosn.getExtraStore());
        if( bankBranchNameStore != null){
            tpaPerson.setBankBranchName(bankBranchNameStore.getName());
        }else{
            tpaPerson.setBankBranchName(collectPerosn.getBranchCodeCn());
        }


       // tpaPerson.setBankBranchName(collectPerosn.getBranchCodeCn());
        tpaPerson.setAccountNo(collectPerosn.getAccountNo());
        tpaPerson.setBankAddress(collectPerosn.getBankAddress());


        Map<String,String> returnExtMap = extendMapToTpaMap(collectPerosn,claim.getBizIdentityCode(),
                FieldModelDefine.领款人信息 ,context.getCollectPersonExtMap(),
                collectPerosn.getExtraProperties() );
        tpaPerson.setExtMap(returnExtMap);


        syncVO.setCollectPersonInfo(tpaPerson);
    }

    private void convertToTpaPerson(ClaimStakeholder saasPerson,
                                            Class clz,
                                            ClaimPersonInfo tpaPerson,
                                            Context context,
                                    Map<String,MetaExtendFieldConfig> extMapDefine) {
        PersonHead annoClass = (PersonHead) clz.getAnnotation(PersonHead.class);
        if( annoClass == null){
            throw new IllegalArgumentException("not find PersonHead annotation");
        }
        //证件类型的页面code
        String identityTypePageCode = PersonFieldTransfer.getPersonPageFieldCode(PersonHolderConstants.identityType,
                clz);
        //从事职业页面code
        String occupationPageCode =  PersonFieldTransfer.getPersonPageFieldCode(PersonHolderConstants.occupation,
                clz);
        //和出险人关系页面code
        String relationToOutInsurePageCode = PersonFieldTransfer.getPersonPageFieldCode(PersonHolderConstants.relationToOutInsure,
                clz);;

        //和主被保险人关系页面code
        String relationToMainInsureCnPageCode = PersonFieldTransfer.getPersonPageFieldCode(PersonHolderConstants.relationToMainInsure,
                clz);;


        FieldModelDefine fieldModelDefine = annoClass.moduleCode();
        tpaPerson.setName(saasPerson.getName());
        tpaPerson.setGender(saasPerson.getGender());
        tpaPerson.setBirthday(saasPerson.getBirthday());

        OptionSetObject identityTypeExtraStore =  ExtraStoreUtil.getOptionSetValue(identityTypePageCode,
                saasPerson.getExtraStore());
        if( identityTypeExtraStore != null){
            tpaPerson.setIdentityTypeCn(identityTypeExtraStore.getName());
        }else{
            tpaPerson.setIdentityTypeCn(saasPerson.getIdentityTypeCn());

        }



       // tpaPerson.setIdentityTypeCn(saasPerson.getIdentityTypeCn());
        tpaPerson.setIdentityNo(saasPerson.getIdentityNo());

        String identityDatePeriod =  saasPerson.getIdentityDatePeriod();
        if( identityDatePeriod == null){
            tpaPerson.setIdentityStart("");
            tpaPerson.setIdentityEnd("");
        }else {
            String[] split = identityDatePeriod.split(",");
            if (split.length == 2) {
                tpaPerson.setIdentityStart(split[0]);
                tpaPerson.setIdentityEnd(split[1]);
            } else {
                tpaPerson.setIdentityStart("");
                tpaPerson.setIdentityEnd("");
            }
        }
        OptionSetObject  occupationExtraStore =  ExtraStoreUtil.getOptionSetValue(occupationPageCode,
                saasPerson.getExtraStore());
        if( occupationExtraStore != null){
            tpaPerson.setOccupationCn(occupationExtraStore.getName());
        }else{
            tpaPerson.setOccupationCn(saasPerson.getOccupationCn());

        }

       // tpaPerson.setOccupationCn(saasPerson.getOccupationCn());
        tpaPerson.setNationality(saasPerson.getNationalityCn());
        tpaPerson.setPhone(saasPerson.getPhone());
        tpaPerson.setContactAddress(saasPerson.getContactAddress());

        List<String> regionCodeList =   getRegionCn(saasPerson.getContactRegion());
        tpaPerson.setContactProvice(regionCodeList.get(0));
        tpaPerson.setContactCity(regionCodeList.get(1));
        tpaPerson.setContactArea(regionCodeList.get(2));

        OptionSetObject  relationToOutInsureStore =  ExtraStoreUtil.getOptionSetValue(relationToOutInsurePageCode,
                saasPerson.getExtraStore());
        if( relationToOutInsureStore != null){
            tpaPerson.setRelationToOutInsureCn(relationToOutInsureStore.getName());
        }else{
            tpaPerson.setRelationToOutInsureCn(saasPerson.getRelationToOutInsureCn());

        }

       // tpaPerson.setRelationToOutInsureCn(saasPerson.getRelationToOutInsureCn());

        OptionSetObject  relationToMainStore =  ExtraStoreUtil.getOptionSetValue(relationToMainInsureCnPageCode,
                saasPerson.getExtraStore());
        if( relationToMainStore != null){
            tpaPerson.setRelationToMainInsureCn(relationToMainStore.getName());
        }else{
            tpaPerson.setRelationToMainInsureCn(saasPerson.getRelationToMainInsureCn());

        }


      //  tpaPerson.setRelationToMainInsureCn(saasPerson.getRelationToMainInsureCn());

        Map<String,String> returnExtMap = extendMapToTpaMap(saasPerson,saasPerson.getBizIdentityCode(),
                fieldModelDefine ,extMapDefine,
                saasPerson.getExtraProperties() );
        tpaPerson.setExtMap(returnExtMap);
    }

    /**
     * 受益人列表
     * @param syncVO
     * @param claim
     * @param context
     */
    private void convertBenifitPersonList(ClaimDetailSyncVO syncVO , Claim claim, Context context){
        Criteria<ClaimStakeholder> personCriteria = new Criteria<>();
        personCriteria.eq(ClaimStakeholder::getRelatedId, claim.getId());
        personCriteria.eq(ClaimStakeholder::getBizIdentityCode,claim.getBizIdentityCode());
        personCriteria.eq(ClaimStakeholder::getPersonType, PersonTypeEnum.BENEFIT.getCode());
        List<ClaimStakeholder> personList = stakeholderRepository.findByCriteria(personCriteria);
        syncVO.setBenefiList(PkListUtil.newArrayList());
        if(PkListUtil.isEmpty(personList)){
            return;
        }
        for(ClaimStakeholder person : personList){
            ClaimPersonInfo claimPersonInfo = new ClaimPersonInfo();
            claimPersonInfo.setTpaBenifitId(person.getTpaId());
            claimPersonInfo.setBenefitPercentage(person.getBenefitPercentage());
            convertToTpaPerson(person,
                    BenefitPerson.class, claimPersonInfo, context, context.getBenifitPersonExtMap());
            // claimPersonInfo.set
            syncVO.getBenefiList().add(claimPersonInfo);
        }

    }

    /**
     * 申请人转化
     * @param syncVO
     * @param claim
     * @param context
     */
    private void convertApplyPerson(ClaimDetailSyncVO syncVO , Claim claim, Context context){
        Criteria<ClaimStakeholder> personCriteria = new Criteria<>();
        personCriteria.eq(ClaimStakeholder::getRelatedId, claim.getId());
        personCriteria.eq(ClaimStakeholder::getBizIdentityCode,claim.getBizIdentityCode());
        personCriteria.eq(ClaimStakeholder::getPersonType, PersonTypeEnum.APPLY.getCode());
        ClaimStakeholder person = PkListUtil.first(stakeholderRepository.findByCriteria(personCriteria));
        if( person == null){
            return;
        }
        ClaimPersonInfo claimPersonInfo = new ClaimPersonInfo();
        convertToTpaPerson(person,
                ApplyPerson.class, claimPersonInfo, context, new HashMap<>());
        // claimPersonInfo.set
        syncVO.setApplyPersonInfo(claimPersonInfo);
    }
    /**
     * 出险人信息
     * @param syncVO
     * @param claim
     * @param context
     */
    private void convertOutInsurePerson(ClaimDetailSyncVO syncVO , Claim claim, Context context){
        Criteria<ClaimStakeholder> personCriteria = new Criteria<>();
        personCriteria.eq(ClaimStakeholder::getRelatedId, claim.getId());
        personCriteria.eq(ClaimStakeholder::getBizIdentityCode,claim.getBizIdentityCode());
        personCriteria.eq(ClaimStakeholder::getPersonType, PersonTypeEnum.OUT_INSURE.getCode());
        ClaimStakeholder person = PkListUtil.first(stakeholderRepository.findByCriteria(personCriteria));
        if( person == null){
            return;
        }
        ClaimPersonInfo claimPersonInfo = new ClaimPersonInfo();
        convertToTpaPerson(person,
                OutInsurePerson.class, claimPersonInfo, context, context.getOutInsurePersonExtMap());
        // claimPersonInfo.set
        syncVO.setOutPersonInfo(claimPersonInfo);
    }
    private void convertMainInsurePerson(ClaimDetailSyncVO syncVO , Claim claim, Context context){
        Criteria<ClaimStakeholder> personCriteria = new Criteria<>();
        personCriteria.eq(ClaimStakeholder::getRelatedId, claim.getId());
        personCriteria.eq(ClaimStakeholder::getBizIdentityCode,claim.getBizIdentityCode());
        personCriteria.eq(ClaimStakeholder::getPersonType, PersonTypeEnum.MAIN_INSURE.getCode());
         ClaimStakeholder person = PkListUtil.first(stakeholderRepository.findByCriteria(personCriteria));
        if( person == null){
            return;
        }
        ClaimPersonInfo claimPersonInfo = new ClaimPersonInfo();
        convertToTpaPerson(person,
                MainInsurePerson.class, claimPersonInfo, context, context.getMainInsurePersonExtMap());
       // claimPersonInfo.set
        syncVO.setMainPersonInfo(claimPersonInfo);
    }

    private void convertInvoice(ClaimDetailSyncVO syncVO , Claim claim, Context context ){
        //invoiceConvert.convertToTpaVo(syncVO, claim, context);
        Criteria<ClaimInvoice> invoiceCriteria = new Criteria<>();
        invoiceCriteria.eq(ClaimInvoice::getRelatedId, claim.getId());
        invoiceCriteria.eq(ClaimInvoice::getBizIdentityCode,claim.getBizIdentityCode());
        List<ClaimInvoice> invoiceList = invoiceRepository.findByCriteria(invoiceCriteria);
        List<InvoiceInfo> tpaInvoiceList = PkListUtil.newArrayList();
        syncVO.setInvoiceList(tpaInvoiceList);
        for(ClaimInvoice saasInvoice : invoiceList){
            ObjInvoke.getObjBigDemical(saasInvoice);
            InvoiceInfo tpaInvoice = new InvoiceInfo();
            tpaInvoiceList.add(tpaInvoice);
            invoiceConvert.convertToTpaVo(tpaInvoice, saasInvoice, context);

            //项目
           // convertProjectInfo(saasInvoice,tpaInvoice , context);
            //费用明细
            Integer inputCheckType = claim.getCfgBizInputType();
            CfgBizInputTypeEnum inputType =   CfgBizInputTypeEnum.getByCode(inputCheckType);
            if(inputCheckType== null || inputType == CfgBizInputTypeEnum.INVOICE_AND_PROJECT){
                //save project 保存项目信息,删除+插入
                convertItemCostInfo(saasInvoice,tpaInvoice , context);
            }



        }
    }
    private void convertItemCostInfo(ClaimInvoice saasInvoice,
                                    InvoiceInfo  tpaInvoice,   Context context){
        Criteria<InvoiceProjectItem> projectCriteria = new Criteria<>();
        projectCriteria.eq(InvoiceProjectItem::getRelatedId, saasInvoice.getId());
        projectCriteria.eq(InvoiceProjectItem::getBizIdentityCode,saasInvoice.getBizIdentityCode());
        List<InvoiceProjectItem> itemList =   itemRepository.findByCriteria(projectCriteria);
        tpaInvoice.setCostItemInfoList(PkListUtil.newArrayList());
        for(InvoiceProjectItem item : itemList){
            ObjInvoke.getObjBigDemical(item);
            CostItemInfo tpaItem = new CostItemInfo();
            tpaInvoice.getCostItemInfoList().add(tpaItem);
            if(StringUtils.isNotBlank(item.getItemUuid())){
                tpaItem.setCostUuid(item.getItemUuid());
            }else{
                //为了做数据兼容，线上不会跑到这里
                String uuid = UUID.randomUUID().toString();
                tpaItem.setCostUuid(uuid);
                item.setItemUuid(uuid);
                InvoiceProjectItem  updto = new InvoiceProjectItem();
                updto.setId(item.getId());
                updto.setItemUuid(uuid);
                itemRepository.update(item);
            }

            OptionSetObject itemProjectCodeStore =  ExtraStoreUtil.getOptionSetValue(CostItemFieldConstants.projectCode,
                    item.getExtraStore());
            if( itemProjectCodeStore != null){
                tpaItem.setProjectName(itemProjectCodeStore.getName());
            }else{
                tpaItem.setProjectName(item.getRelatedProjectName());
            }

           // tpaItem.setProjectName(item.getRelatedProjectName());

            tpaItem.setDrugName(item.getItemName());
            tpaItem.setItemUnit(item.getCount());
            tpaItem.setInvoiceUuid(saasInvoice.getInvoiceUuid());
            MedicalCostTypeEnum costTypeEnum = MedicalCostTypeEnum.getByCode(item.getMedicalType());
            tpaItem.setMedicalTypeCn(costTypeEnum == null?"":costTypeEnum.getDesc());

            tpaItem.setSelfPayPercent(item.getChargingPercentage());
            tpaItem.setUnitPrice(item.getPrice());
            tpaItem.setPayAmount(item.getChargingAmount());
            tpaItem.setOccurAmount(item.getItemTotalAmount());

            // CostItemFieldConstants.dosageForm

            OptionSetObject dosageFormOptionSet =  ExtraStoreUtil.getOptionSetValue(CostItemFieldConstants.dosageForm,
                    item.getExtraStore());
            if( dosageFormOptionSet != null){
                tpaItem.setDosageFormCn(dosageFormOptionSet.getName());
            }else{
                tpaItem.setDosageFormCn(item.getDosageFormCn());
            }
           // tpaItem.setDosageFormCn(item.getDosageFormCn());
            Map<String,String> returnExtMap = extendMapToTpaMap(item,saasInvoice.getBizIdentityCode(),
                    FieldModelDefine.发票费用明细,context.getProjectItemExtMap(),
                    item.getExtraProperties() );
            tpaItem.setExtMap(returnExtMap);

        }
    }

    /**
     * 设置项目
     * @param saasInvoice
     * @param context
     */
    private void convertProjectInfo(ClaimInvoice saasInvoice,
                                    InvoiceInfo  tpaInvoice,   Context context){
        /*Criteria<InvoiceProject> projectCriteria = new Criteria<>();
        projectCriteria.eq(InvoiceProject::getRelatedId, saasInvoice.getId());
        List<InvoiceProject> projectList =   projectRepository.findByCriteria(projectCriteria);
        tpaInvoice.setProjectInfoList(PkListUtil.newArrayList());
        for(InvoiceProject project : projectList){
            ProjectInfo tpaProject = new ProjectInfo();
            tpaInvoice.getProjectInfoList().add(tpaProject);
            tpaProject.setProjectName(project.getProjectName());
            tpaProject.setInvoiceUuid(saasInvoice.getInvoiceUuid());
            tpaProject.setAmount(project.getInvoiceAmount());
            tpaProject.setAllSelfPayAmount(project.getProjectSelfPayAmount());
            tpaProject.setPartSelfPayAmount(project.getProjectPartSelfPayAmount());
            tpaProject.setThirdPayAmount(project.getProjectThirdPartyPayAmount());
            tpaProject.setReasonableAmount(project.getProjectReasonableAmount());
            tpaProject.setUnReasonableAmount(project.getProjectUnreasonableAmount());

            Map<String,String> returnExtMap = extendMapToTpaMap(context.getBizIdentityCode(),
                    FieldModelDefine.发票项目信息,context.getProjectExtMap(),project.getExtraProperties() );
            tpaProject.setExtMap(returnExtMap);
        }*/
    }



}
