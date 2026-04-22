package com.bone.masterdata.adapter.web.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class MasterDataFieldDetailResp {
    private Long id;
    private Long masterDataEntityId;
    private String name;
    private String code;
    private String type;
    private Integer length;
    private Boolean required;
    private String defaultValue;
    private String description;
    private Integer sortOrder;
    private Date createTime;
    private Date updateTime;
}
