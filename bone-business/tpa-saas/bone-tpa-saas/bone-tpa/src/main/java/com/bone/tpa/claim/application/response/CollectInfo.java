package com.bone.tpa.claim.application.response;

import com.bone.core.domain.extension.ExtensibleObject;
import com.bone.tpa.sdk.claim.model.Claim;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 领款人类型
 *
 * 蠢出新境界了
 */
@Data
@Schema(name = "collectInfo", description = "领款人信息", type = "noTime")
public class CollectInfo extends ExtensibleObject<Claim,Long> {

    @Schema(description = "领款人类型", format = "SelectDrop")
    private String collectType;
    private  Map<String,Object>  extraStore;

 /*   @Schema(description = "领款人信息专属字段")
    private Map<String, Object> extraProperties = new HashMap<>(64);*/
}
