import { configType } from "./type";
import { AggregateMethod, AggregateMethodType } from "./type";

// 过滤并转换为数字
const filterAndConvertToNumbers = (values: configType[]): number[] => {
  return values
    .filter((val): val is number | string => val !== null && val !== undefined)
    .map((val) => {
      if (typeof val === "number") return val;
      if (typeof val === "string") {
        const trimmed = val.trim();
        const converted = Number(trimmed);
        return isFinite(converted) ? converted : NaN;
      }
      return NaN;
    })
    .filter((val) => !isNaN(val));
};

export class CountMethod implements AggregateMethod {
  calculate(values: configType[]): number | null {
    return values.length;
  }
}

export class SumMethod implements AggregateMethod {
  calculate(values: configType[]): number | null {
    const numbers = filterAndConvertToNumbers(values);
    if (numbers.length === 0) return null;
    const sum = numbers.reduce((sum, val) => sum + val, 0);
    return parseFloat(sum.toFixed(2));
  }
}

export class AverageMethod implements AggregateMethod {
  calculate(values: configType[]): number | null {
    const numbers = filterAndConvertToNumbers(values);
    if (numbers.length === 0) return null;
    const sum = numbers.reduce((acc, val) => acc + val, 0);
    return parseFloat((sum / numbers.length).toFixed(2));
  }
}

export class SubMethod implements AggregateMethod {
  calculate(values: configType[]): number | null {
    const numbers = filterAndConvertToNumbers(values);
    if (numbers.length === 0) return null;

    // 第一个数字作为被减数，后续数字都从它减掉
    const result = numbers.reduce((acc, val, index) => {
      if (index === 0) {
        return val; // 第一个数字作为被减数
      }
      return acc - val;
    }, 0);
    return parseFloat(result.toFixed(2));
  }
}

export class MulMethod implements AggregateMethod {
  calculate(values: configType[]): number | null {
    const numbers = filterAndConvertToNumbers(values);
    if (numbers.length === 0) return null;
    const result = numbers.reduce((acc, val) => acc * val, 1);
    return parseFloat(result.toFixed(2));
  }
}

export class DivMethod implements AggregateMethod {
  calculate(values: configType[]): number | null {
    const numbers = filterAndConvertToNumbers(values);
    if (numbers.length === 0) return null;

    //第一个字段为被除数，后续字段为除数，遇到除数为0跳过
    const result = numbers.reduce((acc, val, index) => {
      if (index === 0) {
        return val; // 第一个数字作为被除数
      }
      if (val === 0) {
        return acc; // 遇到除数为0时跳过
      }
      return acc / val;
    }, 0);
    return parseFloat(result.toFixed(2));
  }
}

export const aggregateMethodFactory = (
  type: AggregateMethodType
): AggregateMethod => {
  switch (type) {
    case AggregateMethodType.COUNT:
      return new CountMethod();
    case AggregateMethodType.SUM:
      return new SumMethod();
    case AggregateMethodType.AVG:
      return new AverageMethod();
    case AggregateMethodType.SUB:
      return new SubMethod();
    case AggregateMethodType.MUL:
      return new MulMethod();
    case AggregateMethodType.DIV:
      return new DivMethod();
    default:
      throw new Error(`不支持的聚合方法: ${type}`);
  }
};
