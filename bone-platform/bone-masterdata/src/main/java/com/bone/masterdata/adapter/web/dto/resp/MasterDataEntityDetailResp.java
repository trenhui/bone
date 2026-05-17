package com.bone.masterdata.adapter.web.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

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
    private int fieldCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
