package com.bone.lowcode.infra.application.vo.groupData;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetBatchDataVO {

    private Byte type;

    private String parentCode;

    private Integer pageNum;

    private Integer pageSize;

    private Integer totalSize;

    private List<SelectDropDataVO> data;

    public GetBatchDataVO(Byte type, String parentCode, Integer pageNum, Integer pageSize) {
        this.type = type;
        this.parentCode = parentCode;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }
}
