package com.bone.masterdata.application.query.qry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterDataFieldListQuery {
    private Long masterDataEntityId;
    private String keyword;
}
