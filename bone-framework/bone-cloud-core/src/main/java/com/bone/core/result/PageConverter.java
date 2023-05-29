package com.bone.core.result;

import org.springframework.data.domain.*;

/**
 * @author renhui.trh
 */
public final class PageConverter {
    /**
     * @param page
     * @return
     */
    public static PageResult toPageResult(Page page) {
        if (page == null) return new PageResult();

        PageResult pageResult = new PageResult(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
        return pageResult;
    }
}
