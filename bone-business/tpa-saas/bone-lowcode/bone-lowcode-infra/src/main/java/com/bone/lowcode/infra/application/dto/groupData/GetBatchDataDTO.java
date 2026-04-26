package com.bone.lowcode.infra.application.dto.groupData;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class GetBatchDataDTO {

    private Integer pageNum = 1;

    private Integer pageSize = 100;

    @Valid
    List<GetBatchDataDTOItem> paramList;
}
