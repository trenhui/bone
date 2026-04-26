package com.bone.tpa.claim.application.request;

import com.bone.core.result.QueryParam;
import com.bone.core.result.SortablePageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QueryListRequest extends SortablePageParam {

    @Schema(description = "父表关联id")
    private String id;

    @Schema(description = "业务主体id")
    private String bizIdentityCode;

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "模型名称")
    private List<String> modelNames = new ArrayList<>();

    @Schema(description = "查询参数")
    private List<QueryParam> queryParams = new ArrayList<>();


    public QueryListRequest() {
        this.setPageSize(0);
        this.setPageNo(0);
    }
}
