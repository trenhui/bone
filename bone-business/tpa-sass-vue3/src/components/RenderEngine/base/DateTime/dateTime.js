import { AlignmentTypeEnum } from "@/enums/baseComp/AlignmentTypeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { DateFormatEnum } from "@/enums/baseComp/DateFormatEnum";
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
    placeholder: {
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
    defaultValue: {
      type: String,
      default: "",
    },
    inputStatus: {
      type: Number,
      default: InputStatusEnum.editable,
    },
    required: {
      type: Number,
      default: RequiredEnum.notRequired,
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
      type: [String, Number],
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
