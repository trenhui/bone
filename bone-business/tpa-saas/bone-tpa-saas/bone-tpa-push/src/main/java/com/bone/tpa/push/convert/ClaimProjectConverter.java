package com.bone.tpa.push.convert;

import com.bone.tpa.push.dto.ClaimProjectDTO;
import com.bone.tpa.sdk.masterdb.model.TbOverProject;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

/**
 * @Author feihaiming
 *
 * @create 2025/5/7 16:58
 */
@Mapper(componentModel = "spring", implementationName = "PushClaimProjectConverterImpl")
public interface ClaimProjectConverter {

    @Mappings({
            @Mapping(source = "costProjectCode",target = "projectCostProjectCode"),
            @Mapping(source = "costProject",target = "projectCostProject"),
            @Mapping(source = "billAmt",target = "projectBillAmt"),
            @Mapping(source = "selfPayAmt",target = "projectSelfPayAmt"),
            @Mapping(source = "selfCashAmt",target = "projectSelfCashAmt"),
            @Mapping(source = "planPayAmt",target = "projectPlanPayAmt"),
            @Mapping(source = "thirdPartyPayAmt",target = "projectThirdPartyPayAmt"),
            @Mapping(source = "deductionRadio",target = "projectDeductionRadio"),
            @Mapping(source = "deductionAmount",target = "projectDeductionAmount"),
            @Mapping(source = "remark",target = "projectRemark"),
            @Mapping(source = "reasonableAmount",target = "projectReasonableAmount"),
            @Mapping(source = "notReasonableAmount",target = "notReasonableAmount"),
            @Mapping(source = "serialNo",target = "serialNo")
    })
    TbOverProject convert2ClaimProject(ClaimProjectDTO projectInfo);

    // Tb* -> DTO
    @InheritInverseConfiguration(name = "convert2ClaimProject")
    ClaimProjectDTO convert2ClaimProjectDTO(TbOverProject entity);
}
