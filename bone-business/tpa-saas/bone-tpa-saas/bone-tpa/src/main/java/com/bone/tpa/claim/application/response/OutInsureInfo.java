package com.bone.tpa.claim.application.response;

import com.bone.core.domain.extension.ExtensibleObject;
import com.bone.tpa.sdk.claim.model.Claim;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 出险信息
 * 业务对象
 */
@Data
@Schema(name = "outInsureInfo", description = "出险信息", type = "noTime")
public class OutInsureInfo  extends ExtensibleObject<Claim,Long> implements Serializable {

    @Schema(description = "出险时间")
    private Date outInsureTime;
    @Schema(description = "出险类型", format = "SelectDrop")
    private String outInsureType;
    @Schema(description = "出险类型中文")
    private String outInsureTypeCn;
    @Schema(description = "出险省市区", format = "SelectCtrl")
    private String outInsureRegion;
    @Schema(description = "出险详细地址")
    private String outInsureAddress;


  /*  @Schema(description = "出险信息专属字段")
    private Map<String, Object> extraProperties = new HashMap<>(64);
*/
  private  Map<String,Object>  extraStore;

    /**
     * 同步提示信息
     */
    private Map<String, String> syncHintMsg = new HashMap<>();
}
