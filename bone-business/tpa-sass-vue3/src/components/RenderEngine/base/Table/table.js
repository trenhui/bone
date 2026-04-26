import { GroupingAggregateStatusEnum } from "@/enums/table/GroupingAggregateStatusEnum";
import { OrderColumnEnableEnum } from "@/enums/table/OrderColumnEnableEnum";
import { SummaryRowStatusEnum } from "@/enums/table/SummaryRowStatusEnum";
import { TableDisplayEnum } from "@/enums/table/TableDisplayEnum";
import {
  LeftColumnFixedEnum,
  RightColumnFixedEnum,
} from "@/enums/table/ColumnFixedEnum";
import { RequiredEnum } from "@/enums";

export function createProps() {
  return {
    id: {
      type: String,
      default: "",
    },
    code: {
      type: String,
      default: "",
    },
    name: {
      type: String,
      default: "",
    },
    prompt: {
      type: String,
      default: "",
    },
    emptyPrompt: {
      type: String,
      default: "",
    },
    display: {
      type: String,
      default: TableDisplayEnum.show,
    },
    requiredData: {
      type: Number,
      default: RequiredEnum.notRequired,
    },
    fieldSortTypeList: {
      type: Array,
      default: () => [],
    },
    initApiParam: {
      type: String,
      default: "",
    },
    parentTableId: {
      type: String,
      default: "",
    },
    operationColumnEnabled: {
      type: Number,
      default: 0,
    },
    operationColumnFixed: {
      type: Number,
      default: 0,
    },
    eventTriggerList: {
      type: Array,
      default: () => [],
    },
    paginationEnabled: {
      type: Number,
      default: 0,
    },
    defaultPageSize: {
      type: Number,
      default: 0,
    },
    displayTotalSize: {
      type: Number,
      default: 0,
    },
    enableDataSummary: {
      type: Number,
      default: SummaryRowStatusEnum.CLOSED,
    },
    enableDataAggregate: {
      type: Number,
      default: GroupingAggregateStatusEnum.CLOSED,
    },
    leftFixed: {
      type: Number,
      default: LeftColumnFixedEnum.DISABLE,
    },
    rightFixed: {
      type: Number,
      default: RightColumnFixedEnum.DISABLE,
    },
    fillScreen: {
      type: Number,
      default: 0,
    },
    initApi: {
      type: String,
      default: "",
    },
    submitApi: {
      type: String,
      default: "",
    },
    deleteApi: {
      type: String,
      default: "",
    },
    enableSearch: {
      type: Number,
      default: 0,
    },
    searchFieldList: {
      type: Array,
      default: () => [],
    },
    enableOrderColumn: {
      type: Number,
      default: OrderColumnEnableEnum.CLOSED,
    },
    editableColumnList: {
      type: Array,
      default: () => [],
    },
    body: {
      type: Array,
      default: () => [],
    },
    modelCodeList: {
      type: Array,
      default: () => [],
    },
  };
}
