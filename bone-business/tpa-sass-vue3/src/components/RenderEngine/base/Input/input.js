import { AlignmentTypeEnum } from "@/enums/baseComp/AlignmentTypeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { InputValueTypeEnum } from "@/enums/baseComp/InputValueTypeEnum";
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
    alignment: {
      type: Number,
      default: AlignmentTypeEnum.Left,
    },
    required: {
      type: Number,
      default: RequiredEnum.notRequired,
    },
    displayed: {
      type: Number,
      default: DisplayedEnum.show,
    },
    prompt: {
      type: String,
      default: "",
    },
    placeholder: {
      type: String,
      default: "",
    },
    limitedLength: {
      type: Number,
      default: 100,
    },
    inputStatus: {
      type: Number,
      default: InputStatusEnum.editable,
    },
    valueType: {
      type: Number,
      default: InputValueTypeEnum.TEXT,
    },
    defaultValue: {
      type: String,
      default: "",
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
    isTableField: {
      type: Boolean,
      default: false,
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
