package com.bone.lowcode.infra.application.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private Long pageNum;

    private Long pageSize;

    private Long totalSize;

    private List<T> rows;

    public PageResult(Long pageNum, Long pageSize, Long totalSize) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.totalSize = totalSize;
    }
}
