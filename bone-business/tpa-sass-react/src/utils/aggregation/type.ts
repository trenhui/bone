export type configType = number | string | undefined | null;

export interface AggregateMethod {
  calculate(values: configType[]): number | null;
}

export enum AggregateMethodType {
  COUNT = "COUNT",
  SUM = "SUM",
  AVG = "AVG",
  SUB = "SUB",
  MUL = "MUL",
  DIV = "DIV",
}
