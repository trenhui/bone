package com.bone.lowcode.infra.infrastructure.feign.bean;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {

    private Integer totalCount;

    private Integer pageSize;

    private Integer totalPage;

    private Integer currPage;

    private List<T> data;
}
