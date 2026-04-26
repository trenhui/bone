import jp from "jsonpath";

export const isJsonPath = (path: string) => {
  return /^\$(\.[a-zA-Z_][a-zA-Z0-9_]*|\[['"][a-zA-Z0-9_]+['"]\]|\[\d+\])*$/.test(
    path
  );
};

export const getValueByJsonPath = (data: any, jsonPath: string) => {
  try {
    return jp.value(data, jsonPath);
  } catch (error: unknown) {
    console.error(error instanceof Error ? error.message : error);
    return null;
  }
};

export const setValueByJsonPath = (data: any, jsonPath: string, value: any) => {
  try {
    jp.value(data, jsonPath, value);
  } catch (error: unknown) {
    console.error(error instanceof Error ? error.message : error);
  }
};
