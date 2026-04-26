import { DateFormatEnum } from "@/enums/baseComp/DateFormatEnum";
import { AlignmentTypeEnum } from "@/enums/baseComp/AlignmentTypeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { DateRangeValueTypeEnum } from "@/enums/baseComp/DateRangeValueTypeEnum";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";

export function createProps() {
  return {
    id: {
      type: String,
      default: "",
      required: true,
    },
    name: {
      type: String,
      default: "",
    },
    code: {
      type: String,
      default: "",
    },
    showName: {
      type: String,
      default: "",
    },
    prompt: {
      type: String,
      default: "",
    },
    placeholderTwo: {
      type: String,
      default: "",
    },
    placeholderThree: {
      type: String,
      default: "",
    },
    alignment: {
      type: Number,
      default: AlignmentTypeEnum.Left,
    },
    dateFormatType: {
      type: Number,
      default: DateFormatEnum.YYYYMMDD,
    },
    earliestDatetimeType: {
      type: Number,
      default: 0,
    },
    latestDatetimeType: {
      type: Number,
      default: 0,
    },
    earliestDatetime: {
      type: String,
      default: "",
    },
    latestDatetime: {
      type: String,
      default: "",
    },
    defaultValue: {
      type: String,
      default: "",
    },
    valueType: {
      type: Number,
      default: DateRangeValueTypeEnum.TEXT,
    },
    inputStatus: {
      type: Number,
      default: InputStatusEnum.editable,
    },
    required: {
      type: Number,
      default: RequiredEnum.required,
    },
    displayed: {
      type: Number,
      default: DisplayedEnum.show,
    },
    isTableField: {
      type: Boolean,
      default: false,
    },
    showFieldLabel: {
      type: Boolean,
      default: true,
    },
    targetValue: {
      type: String,
      default: "",
    },
    targetProp: {
      type: String,
      default: "",
    },
    isView: {
      type: Boolean,
      default: false,
    },
  };
}

export const createEmits = () => {
  return ["change", "blur"];
};
