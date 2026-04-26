import DataAPI from "@/api/data";

export const useTableSave = () => {
  const batchSave = async (saveInfo, data) => {
    if (!saveInfo || !data?.length) {
      return { success: false, message: "保存信息或数据为空" };
    }

    try {
      // 并行处理所有保存请求
      await Promise.all(data.map((item) => save(saveInfo, item)));

      return { success: true, message: "批量保存成功" };
    } catch (error) {
      console.error("批量保存失败:", error);
      return {
        success: false,
        message: "批量保存失败",
        error: error.message,
      };
    }
  };

  const save = async (saveInfo, data) => {
    if (saveInfo) {
      const updateData = {
        ...data,
        ...saveInfo.params,
        // 主要考虑relatedId需要放在main中
        main: data.main ? { ...data.main, ...saveInfo.params } : undefined,
      };

      await DataAPI.updateListRow({
        updateData,
        modelNames: saveInfo.modelNames,
      });
    }
  };

  return {
    batchSave,
    save,
  };
};
