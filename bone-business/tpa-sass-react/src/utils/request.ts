import axios, { InternalAxiosRequestConfig, AxiosResponse } from "axios";
import { message, notification } from "antd";
import { TOKEN_KEY } from "@/enums/CacheEnum";
import { ResultEnum } from "@/enums/ResultEnum";
import qs from "qs";

// 处理401未授权错误
const handle401Error = () => {
  notification.info({
    message: "提示",
    description: "您的会话已过期，请重新登录",
  });
  localStorage.removeItem(TOKEN_KEY);
  setTimeout(() => {
    location.reload();
  }, 1000);
};

// 创建 axios 实例
const service = axios.create({
  baseURL: "",
  timeout: 90000,
  headers: { "Content-Type": "application/json;charset=utf-8" },
  paramsSerializer: (params) => {
    return qs.stringify(params);
  },
});

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const accessToken = localStorage.getItem(TOKEN_KEY);
    if (accessToken) {
      config.headers.Authorization = accessToken;
    }
    return config;
  },
  (error: any) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse) => {
    // 检查配置的响应类型是否为二进制类型（'blob' 或 'arraybuffer'）, 如果是，直接返回响应对象
    if (
      response.config.responseType === "blob" ||
      response.config.responseType === "arraybuffer"
    ) {
      return response;
    }

    const { code, data, message: resMessage } = response.data;
    if (code === ResultEnum.SUCCESS || code === 0) {
      return data;
    }

    if (code === 401) {
      handle401Error();
      return Promise.reject(new Error(resMessage || "会话已过期，请重新登录"));
    }

    message.error(resMessage || "系统出错，请联系管理员");
    return Promise.reject(new Error(resMessage || "Error"));
  },
  (error: any) => {
    // 异常处理
    if (error.response && error.response.status === 401) {
      handle401Error();
      return;
    }
    if (error.response && error.response.data) {
      const { message: resMessage } = error.response.data;
      message.error(resMessage || "系统出错，请联系管理员");
    }
    return Promise.reject(error.message);
  }
);

// 导出 axios 实例
export default service;
