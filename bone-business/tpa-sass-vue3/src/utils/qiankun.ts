import { qiankunWindow } from "vite-plugin-qiankun/dist/helper";

/* 判断是否乾坤环境 */
export const isQiankun: () => boolean = () => {
  return qiankunWindow.__POWERED_BY_QIANKUN__ || false;
};
