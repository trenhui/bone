package com.bone.metadata.sdk.test.domain.request;

import com.bone.core.model.SortablePageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserSearchRequest extends SortablePageParam {
    private String name;
    private Long roleId;
    private Integer pageNumber = 1;
    private Integer pageSize = 20;
    private Integer offset;

    public Integer getOffset() {
        return offset = (pageNumber - 1) * pageSize;
    }
}