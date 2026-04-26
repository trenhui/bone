package com.bone.lowcode.infra.application.vo.page.pageJson;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewPageVO {

    private PageVO schema;

    private PageRules pageRules;

    private TableRules tableRules;

    public NewPageVO(PageVO pageVO, PageRules pageRules) {
        this.schema = pageVO;
        this.pageRules = pageRules;
    }
}
