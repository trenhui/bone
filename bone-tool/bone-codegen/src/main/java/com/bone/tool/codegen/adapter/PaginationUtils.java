package com.bone.tool.codegen.adapter;

import java.util.Collections;
import java.util.List;

/**
 * 分页工具类
 * <p>
 * 提供统一的分页处理方法，避免在多个服务中重复实现分页逻辑
 */
public class PaginationUtils {
    
    // 默认分页参数
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int DEFAULT_PAGE_NO = 1;
    
    /**
     * 对列表进行分页处理
     * 
     * @param list 原始数据列表
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @param <T> 列表元素类型
     * @return 分页后的列表
     */
    public static <T> List<T> paginateList(List<T> list, int pageNo, int pageSize) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 参数校验
        if (pageNo < 1) {
            pageNo = DEFAULT_PAGE_NO;
        }
        if (pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        // 限制最大页大小
        pageSize = Math.min(pageSize, 1000);
        
        // 计算起始和结束索引
        int start = (pageNo - 1) * pageSize;
        int end = Math.min(start + pageSize, list.size());
        
        // 检查起始索引是否超出范围
        if (start >= list.size()) {
            return Collections.emptyList();
        }
        
        return list.subList(start, end);
    }
    
    /**
     * 计算总页数
     * 
     * @param total 总记录数
     * @param pageSize 每页大小
     * @return 总页数
     */
    public static int calculateTotalPage(int total, int pageSize) {
        if (total <= 0) {
            return 0;
        }
        if (pageSize <= 0) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        return (int) Math.ceil((double) total / pageSize);
    }
    
    /**
     * 校验并规范化页码
     * 
     * @param pageNo 页码
     * @return 有效的页码
     */
    public static int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? DEFAULT_PAGE_NO : pageNo;
    }
    
    /**
     * 校验并规范化每页大小
     * 
     * @param pageSize 每页大小
     * @return 有效的每页大小
     */
    public static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        // 限制最大页大小
        return Math.min(pageSize, 1000);
    }
}