import { AlignmentTypeEnum } from "@/enums/baseComp/AlignmentTypeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { DataFormatEnum } from "@/enums/baseComp/DataFormatEnum";
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
    defaultValue: {
      type: [Number, String],
      default: "",
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
      type: [Number, String],
      default: undefined,
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
