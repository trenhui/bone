package com.bone.tpa.push.convert;

import com.bone.tpa.push.dto.ClaimDrugDTO;
import com.bone.tpa.sdk.masterdb.model.TbOverDrug;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

/**
 * @Author feihaiming
 *
 * @create 2025/5/7 16:58
 */
@Mapper(componentModel = "spring", implementationName = "PushClaimDrugConverterImpl")
public interface ClaimDrugConverter {

    @Mappings({
            @Mapping(source = "billCode",target = "drugBillCode"),
            @Mapping(source = "goodsName",target = "drugGoodsName"),
            @Mapping(source = "dosageForm",target = "drugDosageForm"),
            @Mapping(source = "costProjectCode",target = "drugCostProjectCode"),
            @Mapping(source = "costProject",target = "drugCostProject"),
            @Mapping(source = "note",target = "drugNote"),
            @Mapping(source = "ybType",target = "drugYbType"),
            @Mapping(source = "count",target = "drugCount"),
            @Mapping(source = "onePrice",target = "drugOnePrice"),
            @Mapping(source = "amt",target = "drugAmt"),
            @Mapping(source = "sincePayProportion",target = "drugSincePayProportion"),
            @Mapping(source = "sincePayAmt",target = "drugSincePayAmt"),
            @Mapping(source = "status",target = "drugStatus"),
            @Mapping(source = "deductProportion",target = "drugDeductProportion"),
            @Mapping(source = "deductAmt",target = "drugDeductAmt"),
            @Mapping(source = "thirdAmt",target = "drugThirdAmt"),
            @Mapping(source = "unreasonableAmt",target = "drugUnreasonableAmt"),
            @Mapping(source = "projectId",target = "projectId"),
            @Mapping(source = "partSelfPayAmt",target = "partSelfPayAmt"),
            @Mapping(source = "planPayAmt",target = "drugPlanPayAmt"),
            @Mapping(source = "compensateAmt",target = "drugCompensateAmt"),
            @Mapping(source = "exclusionAmt",target = "drugExclusionAmt"),
            @Mapping(source = "medicalInsurance",target = "medicalInsurance")
    })
    TbOverDrug convert2ClaimDrug(ClaimDrugDTO drugInfo);

    @InheritInverseConfiguration(name = "convert2ClaimDrug")
    ClaimDrugDTO convert2ClaimDrugDTO(TbOverDrug entity);
}
