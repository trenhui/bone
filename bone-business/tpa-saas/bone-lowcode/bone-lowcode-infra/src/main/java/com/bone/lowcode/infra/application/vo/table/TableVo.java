package com.bone.lowcode.infra.application.vo.table;

import com.bone.lowcode.infra.application.vo.page.pageJson.EventTrigger;
import com.bone.lowcode.infra.domain.model.TableFieldSortType;
import lombok.Data;

import java.util.List;

@Data
public class TableVo {

    private String id;

    private String prompt;//提示语
    private String emptyPrompt;//空数据提示语

    private String modelType;//模型类别
    private String tableName;//表名

    private List<ModelOfTable> dataModel;
    private List<TableFieldSortType> fieldSortTypeList;

    private Byte enableOrderColumn;//是否开启序号列,1：开启，0：不开启

    //左固定方式，0：不固定，1：前一列固定，2：前二列固定，3：前三列固定
    private Byte leftFixed;
    //右固定方式，0：不固定，1：最后一列固定，2：最后二列固定
    private Byte rightFixed;
    //是否占满剩余屏幕，0：否，1：是
    private Byte fillScreen;

    private Byte operationColumnEnabled;//是否开启操作列，1：开启，0：不开启
    private Byte operationColumnFixed;//是否固定操作列，1：固定，0：不固定

    private String display;//表格显示还是隐藏
    private Byte requiredData;//提交时是否要求有数据,1:必须有,0:可以没有

    private List<EventTrigger> eventTriggerList;//事件触发器

    private Byte paginationOpened;//显示分页，1：显示，0：不显示
    private Integer defaultPageSize;//默认页行数
    private Byte displayTotalPage;//显示总页数，1：显示，0：不显示
    private Byte displayTotalSize;//显示总条数，1：显示，0：不显示

    private Byte enableDataSummary;//是否开启数据汇总，0：否，1：是
    private Byte enableDataAggregate;//是否是否开启数据分组聚合，0：否，1：是
}
