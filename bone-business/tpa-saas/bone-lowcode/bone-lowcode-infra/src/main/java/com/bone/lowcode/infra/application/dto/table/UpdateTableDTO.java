package com.bone.lowcode.infra.application.dto.table;

import com.bone.lowcode.infra.application.dto.event.CreateEventTriggerDTO;
import com.bone.lowcode.infra.domain.model.DataSummaryRule;
import com.bone.lowcode.infra.domain.model.TableFieldSortType;
import lombok.Data;

import java.util.List;

@Data
public class UpdateTableDTO {

    private Long id;

    private String prompt;
    private String emptyPrompt;

    private List<TableFieldSortType> fieldSortTypeList;

    private Byte enableOrderColumn;//是否开启序号列,1：开启，0：不开启

    //左固定方式，0：不固定，1：前一列固定，2：前二列固定，3：前三列固定
    private Byte leftFixed;
    //右固定方式，0：不固定，1：最后一列固定，2：最后二列固定
    private Byte rightFixed;
    //是否占满剩余屏幕，0：否，1：是
    private Byte fillScreen;

    private Byte operationColumnEnabled;
    private Byte operationColumnFixed;

    private String display;//表格显示还是隐藏
    private Byte requiredData;//提交时是否要求有数据,1:必须有,0:可以没有

    private List<TableEventTriggerDTO> rowEventList; //表格行事件
    private List<TableEventTriggerDTO> leftTableHeadEventList; //左表头事件
    private List<TableEventTriggerDTO> rightTableHeadEventList; //右表头事件

    private Byte paginationOpened;
    private Integer defaultPageSize;
    private Byte displayTotalPage;
    private Byte displayTotalSize;

    private List<ModelOfTableChange> modelOfTableChangelist;
    private List<FieldOfTableChange> fieldOfTableChangeList;

    private Byte enableDataSummary;//是否开启数据汇总，0：否，1：是

    private List<DataSummaryRule> dataSummaryRuleList;

    private Byte enableDataAggregate;//是否是否开启数据分组聚合，0：否，1：是

    private List<CreateEventTriggerDTO> eventTriggerList;
}
