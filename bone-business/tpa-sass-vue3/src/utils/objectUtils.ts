/**
 * 递归合并对象
 * 将源对象的属性合并到目标对象，如果源对象的属性为undefined或null则保留目标对象的值
 * @param target 目标对象
 * @param source 源对象
 * @returns 合并后的对象
 */
export function mergeObjects<T>(target: T, source?: Partial<T> | null): T {
  if (!source) {
    return target;
  }

  // 使用类型断言确保可以安全地操作对象
  const targetObj = target as Record<string, any>;
  const sourceObj = source as Record<string, any>;

  // 先处理目标对象已有的键
  Object.keys(targetObj).forEach((key) => {
    // 如果源对象没有该属性或属性为null/undefined，保留目标对象的值
    if (sourceObj[key] === undefined || sourceObj[key] === null) {
      return;
    }

    // 如果是对象且不是数组，递归合并
    if (
      typeof targetObj[key] === "object" &&
      targetObj[key] !== null &&
      !Array.isArray(targetObj[key]) &&
      typeof sourceObj[key] === "object" &&
      sourceObj[key] !== null &&
      !Array.isArray(sourceObj[key])
    ) {
      targetObj[key] = mergeObjects(targetObj[key], sourceObj[key]);
    }
    // 如果是空数组，保留默认值
    else if (Array.isArray(sourceObj[key]) && sourceObj[key].length === 0) {
      return;
    }
    // 否则直接赋值
    else {
      targetObj[key] = sourceObj[key];
    }
  });

  // 处理源对象中存在但目标对象中不存在的键
  Object.keys(sourceObj).forEach((key) => {
    // 如果目标对象中不存在该键，且源对象的值不为null或undefined
    if (
      !(key in targetObj) &&
      sourceObj[key] !== null &&
      sourceObj[key] !== undefined
    ) {
      targetObj[key] = sourceObj[key];
    }
  });

  return target;
}
