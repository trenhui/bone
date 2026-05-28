import axios from 'axios';

/**
 * 为所有 axios 请求附加 {@code Authorization: Bearer <token>}（与 bone-iam 登录落地一致）。
 * bone-system 控制台 API 自 v2.0.1 起要求有效 JWT + {@code sys:console:read}。
 */
export function setupAxiosAuthInterceptor(): void {
  axios.interceptors.request.use((config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers = config.headers ?? {};
      if (!config.headers.Authorization) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    }
    return config;
  });
}
