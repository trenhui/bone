package com.bone.lowcode.infra.application.vo.processPage;

import lombok.Data;

import java.util.List;

@Data
public class ProcessListPageVO {

    private List<String> modelNameList;

    private ProcessPageBaseInfo pageBaseInfo;

    private ProcessPageHead pageHead;

    private List<Object> pageBody;

    private List<Object> uploadComponentList;

    private String tableId; //表格id

    /**
     * 是否开启tab页,0:否,1:是
     */
    private Byte enableTab;

    /**
     * tab页条件列表
     */
    private List<TabConditionVO> tabConditionList;

    /**
     * 数据范围,1:全局,2:根据账号,3:根据字段
     */
    private Byte dataRange;
}
