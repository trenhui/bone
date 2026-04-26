package com.bone.tpa.push.convert;

import com.bone.tpa.push.dto.ClaimImageDTO;
import com.bone.tpa.sdk.masterdb.model.TbPushImage;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

/**
 * @Author feihaiming
 *
 * @create 2025/5/7 16:58
 */
@Mapper(componentModel = "spring", implementationName = "PushClaimImageConverterImpl")
public interface ClaimImageConverter {

    @Mappings({
            @Mapping(source = "claimCode",target = "claimCode"),
            @Mapping(source = "name",target = "imageName"),
            @Mapping(source = "type",target = "imageType"),
            @Mapping(source = "address",target = "imageAddress"),
            @Mapping(source = "status",target = "imageStatus")
    })
    TbPushImage convert2TbPushImage(ClaimImageDTO imageInfo);

    // Tb* -> DTO
    @InheritInverseConfiguration(name = "convert2TbPushImage")
    ClaimImageDTO convert2ClaimImageDTO(TbPushImage entity);
}
