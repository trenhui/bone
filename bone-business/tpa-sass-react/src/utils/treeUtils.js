/**
 * 映射树
 * @param node 节点
 * @param subNodeGetter 获取子节点的方法
 * @param subNodeSetter 设置子节点的方法
 * @param mapper 映射器
 */
export const treeMapping = (node, subNodeGetter, subNodeSetter, mapper) => {
  // map node
  const targetNode = mapper(node);
  // map children
  const subNodes = subNodeGetter(node);
  if (subNodes != null) {
    const targetSubNodes = subNodes.map((subNode) =>
      treeMapping(subNode, subNodeGetter, subNodeSetter, mapper)
    );
    subNodeSetter(targetNode, targetSubNodes);
  }
  return targetNode;
};

/**
 * 根据id更新嵌套结构对象的属性值
 * @param obj 嵌套结构对象
 * @param targetId 目标对象的id
 * @param targetProperty 目标属性名
 * @param newValue 新的属性值
 */
export const updateNestedObjectById = (
  obj,
  targetId,
  targetProperty,
  newValue
) => {
  // 递归函数
  function traverse(currentObj) {
    // 检查当前对象是否包含目标id

    if ("id" in currentObj && currentObj.id === targetId) {
      // 找到匹配项，更新目标属性
      currentObj[targetProperty] = newValue;
    }

    // 遍历当前对象的所有可枚举属性
    for (const key in currentObj) {
      if (Object.prototype.hasOwnProperty.call(currentObj, key)) {
        const value = currentObj[key];

        // 如果当前值是一个对象（不是null），则递归遍历
        if (typeof value === "object" && value !== null) {
          traverse(value);
        }
      }
    }
  }
  // 调用递归函数
  traverse(obj);
};

/**
 * 根据id查询嵌套结构对象中的目标对象
 * @param obj 嵌套结构对象
 * @param targetId 目标对象的id
 * @returns 目标对象
 */
export const findNestedObjectById = (obj, targetId) => {
  // 递归函数
  function traverse(currentObj) {
    // 检查当前对象的id是否匹配
    if ("id" in currentObj && currentObj.id === targetId) {
      return currentObj; // 找到匹配项，返回当前对象
    }

    // 遍历当前对象的所有属性
    for (const key in currentObj) {
      if (Object.prototype.hasOwnProperty.call(currentObj, key)) {
        const value = currentObj[key];

        // 如果当前属性的值是一个对象，则递归查找
        if (typeof value === "object" && value !== null) {
          const result = traverse(value);

          if (result !== undefined) {
            return result; // 如果找到匹配项，则立即返回
          }
        }
      }
    }

    // 如果没有找到匹配项，则返回undefined
    return undefined;
  }

  return traverse(obj); // 从根对象开始遍历
};
