package com.bone.lowcode.infra.application.vo.page.pageJson;

import com.bone.lowcode.infra.domain.model.DataSummaryRule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableDataSummaryRule {

    private String tableId;

    private List<DataSummaryRule> dataSummaryRuleList;
}
