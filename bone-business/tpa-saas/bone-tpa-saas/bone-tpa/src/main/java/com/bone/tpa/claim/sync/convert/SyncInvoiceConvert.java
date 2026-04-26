package com.bone.tpa.claim.sync.convert;

import com.bone.metadata.sdk.enums.FieldModelDefine;
import com.bone.core.util.DateParserUtil;
import com.bone.tpa.api.enums.*;
import com.bone.tpa.api.vo.InvoiceInfo;
import com.bone.tpa.claim.domain.service.ClaimInvoiceService;
import com.bone.tpa.claim.sync.Constant.InvoiceFieldsConstants;
import com.bone.tpa.claim.sync.Context;
import com.bone.tpa.claim.sync.SyncBaseTool;
import com.bone.tpa.core.util.ExtraStoreUtil;
import com.bone.tpa.facade.vo.OptionSetDTO;
import com.bone.tpa.intelligent.adjustment.differ.InsureanceEventFactory;
import com.bone.tpa.intelligent.adjustment.differ.InsurenceObjectEvent;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.util.OptionSetObject;
import com.bone.tpa.sdk.util.SpringContextUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SyncInvoiceConvert extends SyncBaseTool {
    @Autowired
    private ClaimInvoiceService claimInvoiceService;



    /**
     * saas 的发票转化成 tpa 的发票
     * @param remoteInvoice
     * @param saasInvoice
     * @param context
     */
    public void convertToTpaVo(InvoiceInfo remoteInvoice,
                               ClaimInvoice saasInvoice,
                               Context context){

        remoteInvoice.setInvoiceUuid(saasInvoice.getInvoiceUuid());
        remoteInvoice.setInvoiceNo(saasInvoice.getInvoiceNo());
        if(StringUtils.isNotBlank(saasInvoice.getInputTypeCn())){
            remoteInvoice.setInputModeCn(saasInvoice.getInputTypeCn());

        }else{
            remoteInvoice.setInputModeCn(InvoiceInputType.录入组.getDesc());

        }
        remoteInvoice.setEInvoiceNo(saasInvoice.getEInvoiceNo());
        remoteInvoice.setVerificationCode(saasInvoice.getVerificationCode());


        //InvoiceFieldsConstants.BILL_TYPE
        OptionSetObject billTypeStore =  ExtraStoreUtil.getOptionSetValue(InvoiceFieldsConstants.BILL_TYPE,
                saasInvoice.getExtraStore());
        if( billTypeStore != null){
            remoteInvoice.setBillTypeCn(billTypeStore.getName());
        }else{
            remoteInvoice.setBillTypeCn(saasInvoice.getBillTypeCn());
        }

       // remoteInvoice.setBillTypeCn(saasInvoice.getBillTypeCn());
        EInvoiceValidResult validResult =   EInvoiceValidResult.getEnumByCode(saasInvoice.getVerifyValid());
        if( validResult != null){
            remoteInvoice.setVerifyValidCn(validResult.getDesc());
        }else{
            remoteInvoice.setVerifyValidCn("");
        }
        remoteInvoice.setVerifyValidTimes(saasInvoice.getVerifyValidTimes());
        remoteInvoice.setPaperType(saasInvoice.getPaperType());
        Date invoiceDaate = saasInvoice.getInvoiceDate();
        if( invoiceDaate == null){
            remoteInvoice.setInvoiceDate(null);
        }else{

            remoteInvoice.setInvoiceDate(DateParserUtil.formatDate(invoiceDaate,"yyyy-MM-dd"));
        }

        String hosptialPeriod =   saasInvoice.getHospitalPeriod();
        if( hosptialPeriod == null){
            hosptialPeriod = ",";
        }
        String[] periodArray = hosptialPeriod.split(",");
        if(periodArray.length == 2){
            String startStr = periodArray[0];
            String endStr = periodArray[1];
            remoteInvoice.setLiveStartDate(startStr);
            remoteInvoice.setLiveEndDate(endStr);
        }else{
            remoteInvoice.setLiveStartDate("");
            remoteInvoice.setLiveEndDate("");
        }

        InvoiceTypeEnum invoiceType = InvoiceTypeEnum.getByCode(saasInvoice.getInvoiceType());
        remoteInvoice.setInvoiceTypeCn(invoiceType == null? "" : invoiceType.getDesc());
        //InvoiceFieldsConstants.visitType
        OptionSetObject visitTypeStore =  ExtraStoreUtil.getOptionSetValue(InvoiceFieldsConstants.visitType,
                saasInvoice.getExtraStore());
        if( visitTypeStore != null){
            remoteInvoice.setTreatmentTypeCn(visitTypeStore.getName());

        }else{
            remoteInvoice.setTreatmentTypeCn(saasInvoice.getVisitTypeCn());

        }

        //remoteInvoice.setTreatmentTypeCn(saasInvoice.getVisitTypeCn());

        //InvoiceFieldsConstants.outInsureReason

        OptionSetObject outInsureReasonStore =  ExtraStoreUtil.getOptionSetValue(InvoiceFieldsConstants.outInsureReason,
                saasInvoice.getExtraStore());
        if( outInsureReasonStore != null){
            remoteInvoice.setOutInsureReasonCn(outInsureReasonStore.getName());

        }else{
            remoteInvoice.setOutInsureReasonCn(saasInvoice.getOutInsureReasonCn());

        }
       // remoteInvoice.setOutInsureReasonCn(saasInvoice.getOutInsureReasonCn());
        remoteInvoice.setInvoiceName(saasInvoice.getInvoiceName());

        //InvoiceFieldsConstants.medicalInstitutionType

        OptionSetObject medicalInstitutionTypeStore =  ExtraStoreUtil.getOptionSetValue(InvoiceFieldsConstants.medicalInstitutionType,
                saasInvoice.getExtraStore());
        if( medicalInstitutionTypeStore != null){
            remoteInvoice.setMedicalTypeCn(medicalInstitutionTypeStore.getName());

        }else{
            remoteInvoice.setMedicalTypeCn(saasInvoice.getMedicalInstitutionTypeCn());

        }
        //remoteInvoice.setMedicalTypeCn(saasInvoice.getMedicalInstitutionTypeCn());
        remoteInvoice.setYbFlag(saasInvoice.getHasYb() );
        //InvoiceFieldsConstants.ybType

        OptionSetObject ybtypeStore =  ExtraStoreUtil.getOptionSetValue(InvoiceFieldsConstants.ybType,
                saasInvoice.getExtraStore());
        if( ybtypeStore != null){
            remoteInvoice.setYbTypeCn(ybtypeStore.getName());

        }else{
            remoteInvoice.setYbTypeCn(saasInvoice.getYbTypeCn());

        }
       // remoteInvoice.setYbTypeCn(saasInvoice.getYbTypeCn());
        OptionSetObject hospitalStore =  ExtraStoreUtil.getOptionSetValue(InvoiceFieldsConstants.hospitalCode,
                saasInvoice.getExtraStore());

        if( hospitalStore != null){
            remoteInvoice.setHospitalName(hospitalStore.getName());
        }else{
            remoteInvoice.setHospitalName(saasInvoice.getHospitalName());

        }
       // remoteInvoice.setHospitalName(saasInvoice.getHospitalName());
        OptionSetObject hospitalDepartmentStore =  ExtraStoreUtil.getOptionSetValue(InvoiceFieldsConstants.hospitalDepartment,
                saasInvoice.getExtraStore());
        if( hospitalDepartmentStore != null){
            remoteInvoice.setHospitalDepartmentCn(hospitalDepartmentStore.getName());
        }else{
            remoteInvoice.setHospitalDepartmentCn(saasInvoice.getHospitalDepartmentName());

        }
        //remoteInvoice.setHospitalDepartmentCn(saasInvoice.getHospitalDepartmentName());

        List<String> regionCodeList =   getRegionCn(saasInvoice.getHospitalRegion());
        remoteInvoice.setHospitalProvince(regionCodeList.get(0));
        remoteInvoice.setHospitalCity(regionCodeList.get(1));


        OptionSetObject diseaseNameStore =  ExtraStoreUtil.getOptionSetValue(InvoiceFieldsConstants.diagnosis,
                saasInvoice.getExtraStore());
        if( diseaseNameStore != null){
            remoteInvoice.setDiseaseName(diseaseNameStore.getName());
        }else{
            remoteInvoice.setDiseaseName(saasInvoice.getDiagnosisCn());

        }
       //remoteInvoice.setDiseaseName(saasInvoice.getDiagnosisCn());
        //InvoiceFieldsConstants.severeNameCode
        OptionSetObject severeNameCodeStore =  ExtraStoreUtil.getOptionSetValue(InvoiceFieldsConstants.severeNameCode,
                saasInvoice.getExtraStore());
        if( severeNameCodeStore != null){
            remoteInvoice.setSevereName(severeNameCodeStore.getName());
        }else{
            remoteInvoice.setSevereName(saasInvoice.getSevereName());

        }

        //remoteInvoice.setSevereName(saasInvoice.getSevereName());
        remoteInvoice.setSevereFlag(saasInvoice.getSevereFlag());


        remoteInvoice.setChronicFlag(saasInvoice.getChronicFlag() );
        //chronicFlag 是否慢病
        saasInvoice.setChronicFlag(remoteInvoice.getChronicFlag() );
        //InvoiceFieldsConstants.chronicNameCode
        OptionSetObject chronicNameCodeStore =  ExtraStoreUtil.getOptionSetValue(InvoiceFieldsConstants.chronicNameCode,
                saasInvoice.getExtraStore());
        if( chronicNameCodeStore != null){
            remoteInvoice.setChronicName(chronicNameCodeStore.getName());
        }else{
            remoteInvoice.setChronicName(saasInvoice.getChronicName());

        }


       // remoteInvoice.setChronicName(saasInvoice.getChronicName());
        remoteInvoice.setSubsidyDays(saasInvoice.getSubsidyDays());
        remoteInvoice.setInvoiceAmount(saasInvoice.getTotalAmount());
        remoteInvoice.setEnteredAmount(saasInvoice.getPoolingThreshold() );

        remoteInvoice.setTotalMedicalFundPayment(saasInvoice.getTotalMedicalFundPayment());
        remoteInvoice.setBasicPoolingAmount(saasInvoice.getBasicPoolingAmount());
        remoteInvoice.setPoolingReimbursementRate(saasInvoice.getPoolingReimbursementRate());
        remoteInvoice.setPartSelfPayAmount(saasInvoice.getSelfPayPart2Amount());
        remoteInvoice.setTotalSelfPayAmount(saasInvoice.getTotalSelfPayAmount());
        remoteInvoice.setClassCSelfPayAmount(saasInvoice.getClassCSelfPayAmount());
        remoteInvoice.setExcessLimitSelfPayAmount(saasInvoice.getExcessLimitSelfPayAmount());
        remoteInvoice.setThirdPartyPaidAmount(saasInvoice.getThirdPartyPaidAmount());
        remoteInvoice.setOtherFundAmount(saasInvoice.getOtherFundAmount());
        remoteInvoice.setValidAmount(saasInvoice.getValidAmount());
        remoteInvoice.setInvalidAmount(saasInvoice.getInvalidAmount());
        remoteInvoice.setRemark(saasInvoice.getRemark());

        OptionSetObject hospitalLevelStore =  ExtraStoreUtil.getOptionSetValue(
                InvoiceFieldsConstants.hospitalLevel,
                saasInvoice.getExtraStore());
        if( hospitalLevelStore != null){
            remoteInvoice.setHospitalLevelCn(hospitalLevelStore.getName());
        }

        remoteInvoice.setAccountNo(saasInvoice.getAccountNo());
        Map<String,String> returnExtMap = extendMapToTpaMap(saasInvoice,saasInvoice.getBizIdentityCode(),
                FieldModelDefine.发票,context.getInvoiceExtMap(),saasInvoice.getExtraProperties() );
        remoteInvoice.setExtMap(returnExtMap);

    }

    public void convertToInvoice(InvoiceInfo remoteInvoice,
                                 ClaimInvoice saasInvoice,
                                 boolean insertTag,
                                 List<String> clearField, Context context,
                                 Map<String,String> invoiceHint){


        saasInvoice.setInvoiceNo(remoteInvoice.getInvoiceNo());
        saasInvoice.setInputTypeCn(remoteInvoice.getInputModeCn());
        //是否电票
        boolean eInvoiceTag = "01".equals(remoteInvoice.getPaperType()) || StringUtils.isNotBlank(remoteInvoice.getEInvoiceNo());
        saasInvoice.setEInvoiceNo(remoteInvoice.getEInvoiceNo());
        InvoicePaperTypeEnum paperTypeEnum =    InvoicePaperTypeEnum.getInvoicePaperTypeEnum(remoteInvoice.getPaperType());
        if( paperTypeEnum == null){
            saasInvoice.setPaperType("");
            invoiceHint.put(InvoiceFieldsConstants.paperType,"发票纸质类型不正确:"+remoteInvoice.getPaperType());
        }else{
            saasInvoice.setPaperType(paperTypeEnum.getCode());
        }
        //票据校验码
        saasInvoice.setVerificationCode(nullIfEmpty(remoteInvoice.getVerificationCode()));
        //billType
        OptionSetDTO billTypeCodeOption =  matchCollectionCodeForSync(remoteInvoice.getBillTypeCn(),InvoiceFieldsConstants.BILL_TYPE,
                context,FieldModelDefine.发票,true,invoiceHint);
        saasInvoice.setBillType(getOptionSetDTOCode(billTypeCodeOption));
        saasInvoice.setBillTypeCn(nullIfEmpty(remoteInvoice.getBillTypeCn()));
        ExtraStoreUtil.fillExtraStoreWithOrigin(saasInvoice,InvoiceFieldsConstants.BILL_TYPE, billTypeCodeOption);

        //verifyValidCn 发票验证中文
        EInvoiceValidResult verifyValidCodeEnum = EInvoiceValidResult.getEnumByDesc(remoteInvoice.getVerifyValidCn());

        if( verifyValidCodeEnum!= null){
            saasInvoice.setVerifyValid(verifyValidCodeEnum.getCode());
        }else {
            saasInvoice.setVerifyValid("");
            invoiceHint.put(InvoiceFieldsConstants.verifyValid,"发票验证结果不在枚举内:"+remoteInvoice.getVerifyValidCn());
        }

        saasInvoice.setVerifyValidTimes(remoteInvoice.getVerifyValidTimes());

        //发票时间
        if(StringUtils.isNotBlank(remoteInvoice.getInvoiceDate())){
            try {
                Date invoiceDate = DateParserUtil.parseDate(remoteInvoice.getInvoiceDate());
                saasInvoice.setInvoiceDate(invoiceDate);
            } catch (Exception e) {
               invoiceHint.put(InvoiceFieldsConstants.invoiceDate,"发票日期格式不正确:"+remoteInvoice.getInvoiceDate());
            }
        }

        StringBuffer periodBuffer = new StringBuffer();
        if(StringUtils.isNotBlank(remoteInvoice.getLiveStartDate())){
            periodBuffer.append(remoteInvoice.getLiveStartDate());
        }
        periodBuffer.append(",");
        if(StringUtils.isNotBlank(remoteInvoice.getLiveEndDate())){
            periodBuffer.append(remoteInvoice.getLiveEndDate());
        }
        //住院时间
        saasInvoice.setHospitalPeriod(periodBuffer.toString());
        saasInvoice.setHospitalDays(calDays(remoteInvoice.getLiveStartDate(),remoteInvoice.getLiveEndDate()));
        //发票类型
        InvoiceTypeEnum invoiceType = InvoiceTypeEnum.getByDesc(remoteInvoice.getInvoiceTypeCn());
        if( invoiceType != null){
            saasInvoice.setInvoiceType(invoiceType.getCode());
        }else{
            saasInvoice.setInvoiceType("");
            invoiceHint.put(InvoiceFieldsConstants.invoiceType,"发票类型不正确, 无法匹配枚举:"+remoteInvoice.getInvoiceTypeCn());
        }



        //就诊类型： 1-门/急诊,2-住院,3-药房,4-其他,5-门诊-慢特病
        OptionSetDTO visitTypeCodeOption =  matchCollectionCodeForSync(remoteInvoice.getTreatmentTypeCn(),InvoiceFieldsConstants.visitType,
                context,FieldModelDefine.发票,true,invoiceHint);
        saasInvoice.setVisitType(getOptionSetDTOCode(visitTypeCodeOption));
        saasInvoice.setVisitTypeCn(nullIfEmpty(remoteInvoice.getTreatmentTypeCn()));
        ExtraStoreUtil.fillExtraStoreWithOrigin(saasInvoice,InvoiceFieldsConstants.visitType, visitTypeCodeOption);
        //发票名称
        saasInvoice.setInvoiceName(nullIfEmpty(remoteInvoice.getInvoiceName()));


        //出险原因：     Jb("疾病",1),
        //    Yw("意外",2),
        //    Txyw("特殊意外",3),
        //    Sy("生育",4),
        //    Tj("体检/疫苗接种",5),
        //    Tsjb("特殊疾病",6);
        OptionSetDTO outInsureReasonCodeOption =  matchCollectionCodeForSync(remoteInvoice.getOutInsureReasonCn(),
                InvoiceFieldsConstants.outInsureReason,
                context,FieldModelDefine.发票,true,invoiceHint);
        saasInvoice.setOutInsureReason(getOptionSetDTOCode(outInsureReasonCodeOption));
        saasInvoice.setOutInsureReasonCn(nullIfEmpty(remoteInvoice.getOutInsureReasonCn()));
        ExtraStoreUtil.fillExtraStoreWithOrigin(saasInvoice,InvoiceFieldsConstants.outInsureReason, outInsureReasonCodeOption);

        //医疗机构类型 ,medicalInstitutionType
        OptionSetDTO medicalInstitutionTypeOptionset =  matchCollectionCodeForSync(remoteInvoice.getMedicalTypeCn(),
                InvoiceFieldsConstants.medicalInstitutionType,
                context,FieldModelDefine.发票,true,invoiceHint);
        saasInvoice.setMedicalInstitutionType(getOptionSetDTOCode(medicalInstitutionTypeOptionset));
        saasInvoice.setMedicalInstitutionTypeCn(nullIfEmpty(remoteInvoice.getMedicalTypeCn()));
        ExtraStoreUtil.fillExtraStoreWithOrigin(saasInvoice,InvoiceFieldsConstants.medicalInstitutionType,
                medicalInstitutionTypeOptionset);

        YesOrNoEnum hasYbType =     YesOrNoEnum.getByCode(remoteInvoice.getYbFlag());
        if( hasYbType == null){
            //清空字段
            clearField.add("has_yb");
            invoiceHint.put(InvoiceFieldsConstants.hasYb,"是否医保类型不正确:"+remoteInvoice.getYbFlag());
        }else{
            saasInvoice.setHasYb(hasYbType.getCode() );

        }
        //医保类型
        OptionSetDTO ybTypeCodeOption =  matchCollectionCodeForSync(remoteInvoice.getYbTypeCn(),InvoiceFieldsConstants.ybType,
                context,FieldModelDefine.发票,true,invoiceHint);
        saasInvoice.setYbType(getOptionSetDTOCode(ybTypeCodeOption));
        saasInvoice.setYbTypeCn(nullIfEmpty(remoteInvoice.getYbTypeCn()));
        ExtraStoreUtil.fillExtraStoreWithOrigin(saasInvoice,InvoiceFieldsConstants.ybType, ybTypeCodeOption);

        //医院
        OptionSetDTO hosptialCodeOption =   matchCollectionCodeWithAlert(remoteInvoice.getHospitalName(),
                InvoiceFieldsConstants.hospitalCode,
                context.getBizIdentityCode(),saasInvoice.getRelatedId(),
                FieldModelDefine.发票,true,false,invoiceHint);
        saasInvoice.setHospitalCode(getOptionSetDTOCode(hosptialCodeOption));
        saasInvoice.setHospitalName(nullIfEmpty(remoteInvoice.getHospitalName()));
        ExtraStoreUtil.fillExtraStoreWithOrigin(saasInvoice,InvoiceFieldsConstants.hospitalCode, hosptialCodeOption);

        if(StringUtils.isBlank(saasInvoice.getHospitalCode())){
                String defaultHospitalName =  SpringContextUtils.getBean(InsureanceEventFactory.class).
                        getDefaultHospitalName(saasInvoice.getRelatedId());
                if(StringUtils.isNotBlank(defaultHospitalName)){
                    //注意，这里是不记录hint 的，也不告警
                    OptionSetDTO hosptialCodeDefaultOption =   matchCollectionCodeWithAlert(defaultHospitalName,
                            InvoiceFieldsConstants.hospitalCode,
                            context.getBizIdentityCode(),
                            saasInvoice.getRelatedId(),
                            FieldModelDefine.发票,false,false,invoiceHint);
                    saasInvoice.setHospitalCode(getOptionSetDTOCode(hosptialCodeDefaultOption));
                    saasInvoice.setHospitalName(nullIfEmpty(defaultHospitalName));
                    ExtraStoreUtil.fillExtraStoreWithOrigin(saasInvoice,InvoiceFieldsConstants.hospitalCode, hosptialCodeDefaultOption);
                }
        }



        //hospitalLevel 医院等级
        OptionSetDTO hospitalLevelCodeOption =  matchCollectionCodeForSync(remoteInvoice.getHospitalLevelCn(),
                InvoiceFieldsConstants.hospitalLevel,
                context,FieldModelDefine.发票,true,invoiceHint);
        saasInvoice.setHospitalLevel(getOptionSetDTOCode(hospitalLevelCodeOption));
        ExtraStoreUtil.fillExtraStoreWithOrigin(saasInvoice,InvoiceFieldsConstants.hospitalLevel, hospitalLevelCodeOption);
        //hospitalTypeCn 医院类型
        OptionSetDTO hospitalTypeCodeOption =  matchCollectionCodeForSync(remoteInvoice.getHospitalTypeCn(),
                InvoiceFieldsConstants.hospitalType,
                context,FieldModelDefine.发票,true,invoiceHint);
        saasInvoice.setHospitalType(getOptionSetDTOCode(hospitalTypeCodeOption));
        ExtraStoreUtil.fillExtraStoreWithOrigin(saasInvoice,InvoiceFieldsConstants.hospitalType, hospitalTypeCodeOption);

        //hospitalRegion 医院省市区
        String hospitalRegion = matchProviceCityAreaWithChinese(remoteInvoice.getHospitalProvince(),
                remoteInvoice.getHospitalCity(),null, 2);
        saasInvoice.setHospitalRegion(hospitalRegion);


        //hospitalDepartmentCn 就诊科室
        OptionSetDTO hospitalDepartmentCodeOption =  matchCollectionCodeForSync(remoteInvoice.getHospitalDepartmentCn(),
                InvoiceFieldsConstants.hospitalDepartment,
                context,FieldModelDefine.发票,true,invoiceHint);
        saasInvoice.setHospitalDepartment(getOptionSetDTOCode(hospitalDepartmentCodeOption));
        saasInvoice.setHospitalDepartmentName(nullIfEmpty(remoteInvoice.getHospitalDepartmentCn()));
        ExtraStoreUtil.fillExtraStoreWithOrigin(saasInvoice,
                InvoiceFieldsConstants.hospitalDepartment, hospitalDepartmentCodeOption);

        //diseaseName 名称
        OptionSetDTO diseaseNameCodeOption =  matchCollectionCodeForSync(remoteInvoice.getDiseaseName(),
                InvoiceFieldsConstants.diagnosis,
                context,FieldModelDefine.发票,true,invoiceHint);
        saasInvoice.setDiagnosis(getOptionSetDTOCode(diseaseNameCodeOption));
        saasInvoice.setDiagnosisCn(nullIfEmpty(remoteInvoice.getDiseaseName()));
        ExtraStoreUtil.fillExtraStoreWithOrigin(saasInvoice,InvoiceFieldsConstants.diagnosis, diseaseNameCodeOption);


        //severeName 重疾名称
        OptionSetDTO severeNameCodeOption =  matchCollectionCodeForSync(remoteInvoice.getSevereName(),
                InvoiceFieldsConstants.severeNameCode,
                context,FieldModelDefine.发票,true,invoiceHint);

        saasInvoice.setSevereName(remoteInvoice.getSevereName());
        saasInvoice.setSevereNameCode(nullIfEmpty(getOptionSetDTOCode(severeNameCodeOption)));
        ExtraStoreUtil.fillExtraStoreWithOrigin(saasInvoice,InvoiceFieldsConstants.severeNameCode, severeNameCodeOption);

        YesOrNoEnum serverFlag =     YesOrNoEnum.getByCode(remoteInvoice.getSevereFlag());
        if( serverFlag == null){
            invoiceHint.put(InvoiceFieldsConstants.severeFlag,"是否重疾不正确:"+remoteInvoice.getSevereFlag());
            clearField.add("severe_flag");

        }else{
            saasInvoice.setSevereFlag(serverFlag.getCode());

        }
        //chronicFlag 是否慢病
        YesOrNoEnum chronicFlag =     YesOrNoEnum.getByCode(remoteInvoice.getChronicFlag());
        if( chronicFlag == null){
            invoiceHint.put(InvoiceFieldsConstants.chronicFlag,"是否慢病不正确:"+remoteInvoice.getSevereFlag());
            clearField.add("chronic_flag");

        }else{
            saasInvoice.setChronicFlag(chronicFlag.getCode());

        }

        OptionSetDTO chronicNameCodeOption =  matchCollectionCodeForSync(remoteInvoice.getChronicName(),
                InvoiceFieldsConstants.chronicNameCode,
                context,FieldModelDefine.发票,true,invoiceHint);
        saasInvoice.setChronicCode(getOptionSetDTOCode(chronicNameCodeOption));
        saasInvoice.setChronicName(nullIfEmpty(remoteInvoice.getChronicName()));
        ExtraStoreUtil.fillExtraStoreWithOrigin(saasInvoice,InvoiceFieldsConstants.chronicNameCode, chronicNameCodeOption);


        //津贴天数
        if(remoteInvoice.getSubsidyDays()!= null){
            saasInvoice.setSubsidyDays(remoteInvoice.getSubsidyDays());
        }else {
            clearField.add("subsidy_days");
        }

        //invoiceAmount
        if( remoteInvoice.getInvoiceAmount()!= null){
            saasInvoice.setTotalAmount(remoteInvoice.getInvoiceAmount());

        }else{
            saasInvoice.setTotalAmount(BigDecimal.ZERO);
        }
       //enteredAmount
        if(remoteInvoice.getEnteredAmount()!= null){
            saasInvoice.setPoolingThreshold(remoteInvoice.getEnteredAmount());

        }else{
            saasInvoice.setPoolingThreshold(BigDecimal.ZERO);
        }

        //totalMedicalFundPayment
        if(remoteInvoice.getTotalMedicalFundPayment()!= null){
            saasInvoice.setTotalMedicalFundPayment(remoteInvoice.getTotalMedicalFundPayment());
        }else {
            saasInvoice.setTotalMedicalFundPayment(BigDecimal.ZERO);
        }


        //basicPoolingAmount
        if(remoteInvoice.getBasicPoolingAmount()!= null){
            saasInvoice.setBasicPoolingAmount(remoteInvoice.getBasicPoolingAmount());

        }else {
            saasInvoice.setBasicPoolingAmount(BigDecimal.ZERO);
        }

        //poolingReimbursementRate
        if( remoteInvoice.getPoolingReimbursementRate() != null){
            saasInvoice.setPoolingReimbursementRate(remoteInvoice.getPoolingReimbursementRate());
        }else {
            saasInvoice.setPoolingReimbursementRate(BigDecimal.ZERO);
        }
       //partSelfPayAmount
        if( remoteInvoice.getPartSelfPayAmount() != null){
            saasInvoice.setSelfPayPart2Amount(remoteInvoice.getPartSelfPayAmount());

        }else {
            saasInvoice.setSelfPayPart2Amount(BigDecimal.ZERO);
        }

        //totalSelfPayAmount
        if(remoteInvoice.getTotalSelfPayAmount() != null){
            saasInvoice.setTotalSelfPayAmount(remoteInvoice.getTotalSelfPayAmount());
        }else{
            saasInvoice.setTotalSelfPayAmount(BigDecimal.ZERO);
        }
       //classCSelfPayAmount
        if(remoteInvoice.getClassCSelfPayAmount()!= null){
            saasInvoice.setClassCSelfPayAmount(remoteInvoice.getClassCSelfPayAmount());

        }else {
            saasInvoice.setClassCSelfPayAmount(BigDecimal.ZERO);
        }
        //excessLimitSelfPayAmount
        if(remoteInvoice.getExcessLimitSelfPayAmount() != null){
            saasInvoice.setExcessLimitSelfPayAmount(remoteInvoice.getExcessLimitSelfPayAmount());

        }else {
            saasInvoice.setExcessLimitSelfPayAmount(BigDecimal.ZERO);
        }
       //thirdPartyPaidAmount
        if(remoteInvoice.getThirdPartyPaidAmount() != null){
            saasInvoice.setThirdPartyPaidAmount(remoteInvoice.getThirdPartyPaidAmount());

        }else {
            saasInvoice.setThirdPartyPaidAmount(BigDecimal.ZERO);
        }
        //otherFundAmount
        if(remoteInvoice.getOtherFundAmount()!= null){
            saasInvoice.setOtherFundAmount(remoteInvoice.getOtherFundAmount());

        }else {
            saasInvoice.setOtherFundAmount(BigDecimal.ZERO);
        }
        //validAmount
        if( remoteInvoice.getValidAmount() != null){
            saasInvoice.setValidAmount(remoteInvoice.getValidAmount());

        }
       //invalidAmount
        if( remoteInvoice.getInvalidAmount() != null){
            saasInvoice.setInvalidAmount(remoteInvoice.getInvalidAmount());

        }else {
            saasInvoice.setInvalidAmount(BigDecimal.ZERO);
        }
        //remark
        saasInvoice.setRemark(nullIfEmpty(remoteInvoice.getRemark()));

        saasInvoice.setAccountNo(nullIfEmpty(remoteInvoice.getAccountNo()));
        //扩展字段
        Map<String,String> extMap =  remoteInvoice.getExtMap();
        if( extMap != null && extMap.size()>0){
            Map<String,String> hintMap = new HashMap<>();
            //发票扩展字段
            Map<String,Object> extendFieldMap = mapToExtendMap(FieldModelDefine.发票,context,saasInvoice,
                    context.getBizIdentityCode(), context.getClaimNumber(),
                    hintMap,clearField,
                    context.getInvoiceExtMap(),extMap,saasInvoice);
            saasInvoice.setExtraProperties(extendFieldMap);
        }


        claimInvoiceService.updateValidMoney(saasInvoice);


    }


    private Integer calDays(String start,String end){

        return DateParserUtil.daysBetween(start,end);
    }
}
