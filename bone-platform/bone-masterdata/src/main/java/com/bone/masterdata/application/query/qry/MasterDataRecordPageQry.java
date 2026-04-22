package com.bone.masterdata.application.query.qry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterDataRecordPageQry {
    @Builder.Default
    private Integer pageNum = 1;
    @Builder.Default
    private Integer pageSize = 10;
    private Long masterDataEntityId;
    private String keyword;
    private String status;
}
