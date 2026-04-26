package com.bone.tpa.claim.sync;

import com.bone.metadata.sdk.dto.MetaFieldDTO;
import com.bone.tpa.facade.vo.ColletionBindVO;
import lombok.Data;

@Data
public class MetaExtendFieldConfig extends MetaFieldDTO {
    /**
     * 如果有绑定选项集合或者主数据，则不为null
     */
    private  ColletionBindVO bindVO;


}
