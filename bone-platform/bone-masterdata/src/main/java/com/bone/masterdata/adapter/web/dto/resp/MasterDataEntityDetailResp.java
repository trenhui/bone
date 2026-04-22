package com.bone.masterdata.adapter.web.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class MasterDataEntityDetailResp {
    private Long id;
    private String name;
    private String code;
    private String description;
    private String category;
    private String status;
    private Integer version;
    private Date createTime;
    private Date updateTime;
}
