package com.bone.tpa.claim.domain.service;

import com.bone.core.util.PkListUtil;
import com.bone.tpa.api.enums.*;
import com.bone.tpa.claim.application.dto.EnumOptionDTO;
import com.bone.tpa.claim.application.dto.EnumSelectDTO;
import com.bone.tpa.sdk.adjustment.enums.PolicyType;
import com.bone.tpa.sdk.claim.enums.ClmProcessType;
import com.bone.tpa.sdk.claim.enums.TpaHangupReason;
import com.bone.tpa.sdk.claim.enums.VisitTypeEnum;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class PageEnumsSelectService {

    public static final String yesOrNo = "yesOrNo";

    public static final String gender = "gender";
    public static final String slipAttribute = "slipAttribute";
    public static final String sourceType = "sourceType";
    public static final String invoiceInputType = "invoiceInputType";
    public static final String collectTransferType = "collectTransferType"; //好像和下面的领款对象是一样的
    public static final String invoicePaperType = "invoicePaperType";
    public static final String collectType = "collectType";
    public static final String visitType = "visitType";

    //医疗发票类型，门诊住院购药
    public static final String invoiceType = "invoiceType";

    //发票验真结果，EInvoiceValidResult
    public static final String verifyValid = "verifyValid";

    //医保费用类型，甲乙丙
    public static final String medicalType = "medicalType";

    //挂起相关信息
    public static final String hangUpStatus = "hangUpStatus";
    public static final String hangUpType = "hangUpType";

    //赔案上的信息
    public static final String bizType = "bizType";
    public static final String processType = "processType";
    public static final String claimSource = "claimSource";

    /**
     * 获取所有可允许的下拉列表
     * @return
     */
    public List<EnumSelectDTO> getallEnumsCodeList(){
        //CollectTypeEnum
        //yesOrNoEnum
        //genderEnum
        //SlipAttribute
        //SourceType
        //InvoiceInputType
        //CollectTransferType
        //InvoicePaperTypeEnum

        //VisitTypeEnum ?
        List<EnumSelectDTO> rs = PkListUtil.newArrayList();
        EnumSelectDTO yesOrNoEnum = new EnumSelectDTO();
        yesOrNoEnum.setCode(yesOrNo);
        yesOrNoEnum.setName("是否");
        rs.add(yesOrNoEnum);

        EnumSelectDTO genderEnum = new EnumSelectDTO();
        genderEnum.setCode(gender);
        genderEnum.setName("性别");
        rs.add(genderEnum);

        EnumSelectDTO slipAttributeEnum = new EnumSelectDTO();
        slipAttributeEnum.setCode(slipAttribute);
        slipAttributeEnum.setName("保单属性");
        rs.add(slipAttributeEnum);

        EnumSelectDTO sourceTypeEnum = new EnumSelectDTO();
        sourceTypeEnum.setCode(sourceType);
        sourceTypeEnum.setName("赔案来源");
        rs.add(sourceTypeEnum);

        EnumSelectDTO invoiceInputTypeEnum = new EnumSelectDTO();
        invoiceInputTypeEnum.setCode(invoiceInputType);
        invoiceInputTypeEnum.setName("录入方式");
        rs.add(invoiceInputTypeEnum);

        EnumSelectDTO collectTransferTypeEnum = new EnumSelectDTO();
        collectTransferTypeEnum.setCode(collectTransferType);
        collectTransferTypeEnum.setName("对公对私");
        rs.add(collectTransferTypeEnum);

        EnumSelectDTO invoicePaperTypeEnum = new EnumSelectDTO();
        invoicePaperTypeEnum.setCode(invoicePaperType);
        invoicePaperTypeEnum.setName("电子纸质");
        rs.add(invoicePaperTypeEnum);

        EnumSelectDTO collectTypeEnum = new EnumSelectDTO();
        collectTypeEnum.setCode(collectType);
        collectTypeEnum.setName("领款人类型");
        rs.add(collectTypeEnum);

        EnumSelectDTO visitTypeEnum = new EnumSelectDTO();
        visitTypeEnum.setCode(visitType);
        visitTypeEnum.setName("就诊类型");
        rs.add(visitTypeEnum);

        EnumSelectDTO invoiceTypeEnum = new EnumSelectDTO();
        invoiceTypeEnum.setCode(invoiceType);
        invoiceTypeEnum.setName("医疗发票类型");
        rs.add(invoiceTypeEnum);

        EnumSelectDTO verifyValidResult = new EnumSelectDTO();
        verifyValidResult.setCode(verifyValid);
        verifyValidResult.setName("发票验真结果");
        rs.add(verifyValidResult);

        EnumSelectDTO medicalTypeEnum = new EnumSelectDTO();
        medicalTypeEnum.setCode(medicalType);
        medicalTypeEnum.setName("医疗费用类型");
        rs.add(medicalTypeEnum);

        EnumSelectDTO hangUpStatusEnum = new EnumSelectDTO();
        hangUpStatusEnum.setCode(hangUpStatus);
        hangUpStatusEnum.setName("挂起状态");
        rs.add(hangUpStatusEnum);

        EnumSelectDTO hangUpTypeEnum = new EnumSelectDTO();
        hangUpTypeEnum.setCode(hangUpType);
        hangUpTypeEnum.setName("挂起类型");
        rs.add(hangUpTypeEnum);

        EnumSelectDTO bizTypeEnum = new EnumSelectDTO();
        bizTypeEnum.setCode(bizType);
        bizTypeEnum.setName("业务类型");
        rs.add(bizTypeEnum);

        EnumSelectDTO processTypeEnum = new EnumSelectDTO();
        processTypeEnum.setCode(processType);
        processTypeEnum.setName("流程类型");
        rs.add(processTypeEnum);

        EnumSelectDTO claimSourceEnum = new EnumSelectDTO();
        claimSourceEnum.setCode(claimSource);
        claimSourceEnum.setName("流程来源");
        rs.add(claimSourceEnum);

        return rs;
    }


    public List<EnumOptionDTO> getOptionSetByEnum(String enumCode){
        List<EnumOptionDTO> rs = PkListUtil.newArrayList();
        if(yesOrNo.equals(enumCode)){
            Arrays.stream(YesOrNoEnum.values()).forEach(t->{
                EnumOptionDTO optionDTO = new EnumOptionDTO();
                optionDTO.setCode(t.getCode().toString());
                optionDTO.setName(t.getDesc());
                rs.add(optionDTO);
            });
            return rs;
        }

        if(gender.equals(enumCode)){
            Arrays.stream(GenderEnum.values()).forEach(t->{
                EnumOptionDTO optionDTO = new EnumOptionDTO();
                optionDTO.setCode(t.getCode());
                optionDTO.setName(t.getDesc());
                rs.add(optionDTO);
            });
            return rs;
        }

        if(slipAttribute.equals(enumCode)){
            Arrays.stream(SlipAttribute.values()).forEach(t->{
                EnumOptionDTO optionDTO = new EnumOptionDTO();
                optionDTO.setCode(t.getCode());
                optionDTO.setName(t.getDesc());
                rs.add(optionDTO);
            });
            return rs;
        }

        if(sourceType.equals(enumCode)){
            Arrays.stream(SourceType.values()).forEach(t->{
                EnumOptionDTO optionDTO = new EnumOptionDTO();
                optionDTO.setCode(t.getCode());
                optionDTO.setName(t.getDesc());
                rs.add(optionDTO);
            });
            return rs;
        }

        if(invoiceInputType.equals(enumCode)){
            Arrays.stream(InvoiceInputType.values()).forEach(t->{
                EnumOptionDTO optionDTO = new EnumOptionDTO();
                optionDTO.setCode(t.getCode());
                optionDTO.setName(t.getDesc());
                rs.add(optionDTO);
            });
            return rs;
        }

        if(collectTransferType.equals(enumCode)){
            Arrays.stream(CollectTransferType.values()).forEach(t->{
                EnumOptionDTO optionDTO = new EnumOptionDTO();
                optionDTO.setCode(t.getCode());
                optionDTO.setName(t.getDesc());
                rs.add(optionDTO);
            });
            return rs;
        }

        if(invoicePaperType.equals(enumCode)){
            Arrays.stream(InvoicePaperTypeEnum.values()).forEach(t->{
                EnumOptionDTO optionDTO = new EnumOptionDTO();
                optionDTO.setCode(t.getCode());
                optionDTO.setName(t.getDesc());
                rs.add(optionDTO);
            });
            return rs;
        }

        if(collectType.equals(enumCode)){
            Arrays.stream(CollectTypeEnum.values()).forEach(t->{
                EnumOptionDTO optionDTO = new EnumOptionDTO();
                optionDTO.setCode(t.getCode());
                optionDTO.setName(t.getDesc());
                rs.add(optionDTO);
            });
            return rs;
        }

        if(visitType.equals(enumCode)){
            Arrays.stream(VisitTypeEnum.values()).forEach(t->{
                EnumOptionDTO optionDTO = new EnumOptionDTO();
                optionDTO.setCode(t.getCode());
                optionDTO.setName(t.getValue());
                rs.add(optionDTO);
            });
            return rs;
        }

        if (invoiceType.equals(enumCode)) {
            Arrays.stream(InvoiceTypeEnum.values()).forEach(t->{
                EnumOptionDTO optionDTO = new EnumOptionDTO();
                optionDTO.setCode(t.getCode());
                optionDTO.setName(t.getDesc());
                rs.add(optionDTO);
            });
            return rs;
        }

        if (verifyValid.equals(enumCode)) {
            Arrays.stream(EInvoiceValidResult.values()).forEach(t->{
                EnumOptionDTO optionDTO = new EnumOptionDTO();
                optionDTO.setCode(t.getCode());
                optionDTO.setName(t.getDesc());
                rs.add(optionDTO);
            });
            return rs;
        }

        if (medicalType.equals(enumCode)) {
            Arrays.stream(MedicalCostTypeEnum.values()).forEach(t->{
                EnumOptionDTO optionDTO = new EnumOptionDTO();
                optionDTO.setCode(t.getCode());
                optionDTO.setName(t.getDesc());
                rs.add(optionDTO);
            });
            return rs;
        }

        if (hangUpStatus.equals(enumCode)) {
            Arrays.stream(HangUpStatus.values()).forEach(t->{
                EnumOptionDTO optionDTO = new EnumOptionDTO();
                optionDTO.setCode(t.getCode());
                optionDTO.setName(t.getDesc());
                rs.add(optionDTO);
            });
            return rs;
        }

        if (hangUpType.equals(enumCode)) {
            Arrays.stream(TpaHangupReason.values()).forEach(t->{
                EnumOptionDTO optionDTO = new EnumOptionDTO();
                optionDTO.setCode(t.getCode());
                optionDTO.setName(t.getDesc());
                rs.add(optionDTO);
            });
            return rs;
        }

        if (bizType.equals(enumCode)) {
            Arrays.stream(PolicyType.values()).forEach(t->{
                EnumOptionDTO optionDTO = new EnumOptionDTO();
                optionDTO.setCode(String.valueOf(t.getCode()));
                optionDTO.setName(t.getDesc());
                rs.add(optionDTO);
            });
            return rs;
        }

        if (processType.equals(enumCode)) {
            Arrays.stream(ClmProcessType.values()).forEach(t->{
                EnumOptionDTO optionDTO = new EnumOptionDTO();
                optionDTO.setCode(t.getCode());
                optionDTO.setName(t.getDesc());
                rs.add(optionDTO);
            });
            return rs;
        }

        if (claimSource.equals(enumCode)) {
            Arrays.stream(SignSystemSourceEnum.values()).forEach(t->{
                EnumOptionDTO optionDTO = new EnumOptionDTO();
                optionDTO.setCode(String.valueOf(t.getCode()));
                optionDTO.setName(t.getDesc());
                rs.add(optionDTO);
            });
            return rs;
        }

        return PkListUtil.newArrayList();
    }


    public EnumOptionDTO getOptionByName(String enumsCode,String name){
        List<EnumOptionDTO>  optionDTOList =   getOptionSetByEnum(enumsCode);
        if(PkListUtil.isEmpty(optionDTOList)){
            return null;
        }
        for (EnumOptionDTO optionDTO : optionDTOList) {
            if(optionDTO.getName().equals(name)){
                return optionDTO;
            }
        }
        return null;
    }

    public EnumOptionDTO getOptionByCode(String enumsCode,String code){
        List<EnumOptionDTO>  optionDTOList =   getOptionSetByEnum(enumsCode);
        if(PkListUtil.isEmpty(optionDTOList)){
            return null;
        }
        for (EnumOptionDTO optionDTO : optionDTOList) {
            if(optionDTO.getCode().equals(code)){
                return optionDTO;
            }
        }
        return null;
    }


}
