package com.bone.demo.infrastructure.config;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 分页结果
 */
@Data
public class PageResult<T> {
    /**
     * 数据列表
     */
    private List<T> records;
    
    /**
     * 总数
     */
    private Long total;
    
    /**
     * 每页大小
     */
    private Integer pageSize;
    
    /**
     * 页码
     */
    private Integer pageNum;
    
    /**
     * 总页数
     */
    private Integer totalPages;
    
    public PageResult() {
        this.records = Collections.emptyList();
        this.total = 0L;
        this.pageSize = 10;
        this.pageNum = 1;
        this.totalPages = 0;
    }
    
    public PageResult(List<T> records, Long total, Integer pageSize, Integer pageNum) {
        this.records = records != null ? records : Collections.emptyList();
        this.total = total != null ? total : 0L;
        this.pageSize = pageSize != null && pageSize > 0 ? pageSize : 10;
        this.pageNum = pageNum != null && pageNum > 0 ? pageNum : 1;
        this.totalPages = (int) Math.ceil((double) this.total / this.pageSize);
    }
}