import { BaseComparator } from "../comparators/BaseComparator";
import { InputComparator } from "../comparators/InputComparator";
import { InputNumComparator } from "../comparators/InputNumComparator";
import { DateTimeComparator } from "../comparators/DateTimeComparator";
import { DateRangeComparator } from "../comparators/DateRangeComparator";
import { SelectDropComparator } from "../comparators/SelectDropComparator";
import { SelectCtrlComparator } from "../comparators/SelectCtrlComparator";
import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";

/**
 * 创建比较器
 * @param baseCompType 组件类型
 * @returns 比较器
 */
export function createComparator(
  baseCompType: BaseCompType
): BaseComparator<any> {
  switch (baseCompType) {
    case BaseCompType.Input:
      return new InputComparator();
    case BaseCompType.InputNum:
      return new InputNumComparator();
    case BaseCompType.DateTime:
      return new DateTimeComparator();
    case BaseCompType.DateRange:
      return new DateRangeComparator();
    case BaseCompType.SelectDrop:
      return new SelectDropComparator();
    case BaseCompType.SelectCtrl:
      return new SelectCtrlComparator();
    default:
      throw new Error("Unsupported component type");
  }
}
