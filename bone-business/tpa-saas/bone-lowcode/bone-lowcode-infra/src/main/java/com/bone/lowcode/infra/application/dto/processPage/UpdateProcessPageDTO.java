package com.bone.lowcode.infra.application.dto.processPage;

import com.bone.lowcode.infra.domain.model.PageHeadField;
import com.bone.lowcode.infra.domain.model.TabCondition;
import com.bone.lowcode.infra.domain.model.TableSearchField;
import lombok.Data;

import java.util.List;

@Data
public class UpdateProcessPageDTO {

    private Long pageId;

    //页面介绍
    private String desc;

    //是否开启tab页,0:否,1:是
    private Byte enableTab;

    //tab页条件列表
    private List<TabCondition> tabConditionList;

    //数据范围,1:全局,2:根据账号,3:根据字段
    private Byte dataRange;

    //是否开启页头,0:否,1:是
    private Byte enablePageHead;

    //页头字段信息
    private List<PageHeadField> pageHeadFieldList;


    // ===以下是表格相关====
    private Long tableId;

    //是否开启搜索栏,0:否,1:是
    private Byte enableSearch;

    //表格搜索字段
    private List<TableSearchField> searchFieldList;
}
