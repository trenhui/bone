import { InputValueTypeEnum } from "@/enums/baseComp/InputValueTypeEnum";
import { DataFormatEnum } from "@/enums/baseComp/DataFormatEnum";
import { DateFormatEnum } from "@/enums/baseComp/DateFormatEnum";
import { FilterTypeEnum } from "@/enums/baseComp/FilterTypeEnum";
import { SelectTypeEnum } from "@/enums/baseComp/SelectTypeEnum";
import { SelectLevelEnum } from "@/enums/baseComp/SelectLevelEnum";

export function createProps() {
  return {
    type: {
      type: String,
      default: "Input",
    },
    limitedLength: {
      type: Number,
      default: 100,
    },
    valueType: {
      type: Number,
      default: InputValueTypeEnum.TEXT,
    },
    dataFormat: {
      type: Number,
      default: DataFormatEnum.number,
    },
    min: {
      type: [Number, String],
      default: null,
    },
    max: {
      type: [Number, String],
      default: null,
    },
    decimalDigit: {
      type: Number,
      default: 0,
    },
    multiples: {
      type: [Number, String],
      default: 0.01,
      validator: (value) => value === null || value === undefined || value >= 0,
    },
    dateFormatType: {
      type: Number,
      default: DateFormatEnum.YYYYMMDD,
    },
    earliestDatetime: {
      type: String,
      default: "",
    },
    earliestDatetimeType: {
      type: Number,
      default: 0,
    },
    latestDatetime: {
      type: String,
      default: "",
    },
    latestDatetimeType: {
      type: Number,
      default: 0,
    },
    selectType: {
      type: Number,
      default: SelectTypeEnum.single,
    },
    filterType: {
      type: Number,
      default: FilterTypeEnum.NotSupported,
    },
    selectDatasource: {
      type: Object,
      default: null,
    },
    selectLevel: {
      type: Number,
      default: SelectLevelEnum.TWO,
    },
  };
}
