import { DisplayModeEnum } from "@/enums/DisplayModeEnum";

export function createProps() {
  return {
    id: {
      type: String,
      default: "",
    },
    name: {
      type: String,
      default: "",
    },
    code: {
      type: String,
      default: "",
    },
    displayMode: {
      type: String,
      default: DisplayModeEnum.VIEW,
    },
    bizInfo: {
      type: Object,
      default: () => {},
    },
    pageType: {
      type: Number,
      default: 0,
    },
    body: {
      type: Array,
      default: () => [],
    },
  };
}
