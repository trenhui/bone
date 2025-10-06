package com.bone.core.model;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 分页参数模型
 */
@Data
public class PageParam implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Integer DEFAULT_PAGE = 1;
    private static final Integer DEFAULT_SIZE = 10;

    @Min(value = 1, message = "页码必须大于等于1")
    private Integer page = DEFAULT_PAGE;

    @Min(value = 1, message = "每页大小必须大于等于1")
    private Integer size = DEFAULT_SIZE;

    public static PageParam of(Integer page, Integer size) {
        PageParam param = new PageParam();
        if (page != null) param.setPage(page);
        if (size != null) param.setSize(size);
        return param;
    }

    public static PageParam of() {
        return new PageParam();
    }
}