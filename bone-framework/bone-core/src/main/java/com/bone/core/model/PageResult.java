package com.bone.core.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果模型
 */
@Data
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<T> records;
    private Long total;
    private Integer page;
    private Integer size;
    private Integer pages;
    private Boolean hasNext;
    private Boolean hasPrevious;

    // 私有构造方法
    private PageResult(List<T> records, Long total, Integer page, Integer size) {
        this.records = records != null ? records : Collections.emptyList();
        this.total = total != null ? Math.max(total, 0L) : 0L;
        this.page = page != null ? Math.max(page, 1) : 1;
        this.size = size != null ? Math.max(size, 1) : 10;
        calculateFields();
    }

    // === 静态工厂方法 ===
    public static <T> PageResult<T> of(List<T> records, Long total, Integer page, Integer size) {
        return new PageResult<>(records, total, page, size);
    }

    public static <T> PageResult<T> of(List<T> records, Long total, PageParam param) {
        return new PageResult<>(records, total, param.getPage(), param.getSize());
    }

    public static <T> PageResult<T> empty() {
        return new PageResult<>(Collections.emptyList(), 0L, 1, 10);
    }

    // === 业务方法 ===
    private void calculateFields() {
        this.pages = (int) Math.ceil((double) this.total / this.size);
        this.hasPrevious = this.page > 1;
        this.hasNext = this.page < this.pages;
    }

    public Boolean isEmpty() {
        return this.records.isEmpty();
    }

    public Integer getRecordCount() {
        return this.records.size();
    }

    public Integer getOffset() {
        return (this.page - 1) * this.size;
    }
}