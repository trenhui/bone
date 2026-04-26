import eventMap from "./eventMap";

const eventRouter = async (eventCode: string, dataGetter: Function) => {
  const eventInfo = eventMap[eventCode];

  if (eventInfo) {
    try {
      // 动态加载模块
      const eventModule = await eventInfo.module;

      // 调用模块中的函数
      if (eventModule[eventInfo.functionName]) {
        eventModule[eventInfo.functionName](dataGetter);
      } else {
        ElMessage.error("未找到该事件对应的处理函数");
      }
    } catch (error: unknown) {
      ElMessage.error(`事件处理发生异常: ${(error as Error).message}`);
      console.error(error);
    }
  } else {
    ElMessage.error("未找到该事件对应的处理模块");
  }
};

export default eventRouter;
