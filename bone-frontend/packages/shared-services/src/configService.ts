import type { ApiResponse, SystemConfig } from '@bone/shared-types';
import { createApiClient } from './apiClient';

const CONFIG_BASE = '/api/v1/system/configs';

/**
 * 系统配置服务（纯 axios，D1）。
 * 读取后端系统配置；未命中时返回调用方提供的 defaultValue 兜底。
 */
export const configService = {
  /** 按 key 读取配置，缺失时返回 defaultValue */
  async get<T = string>(key: string, defaultValue?: T): Promise<T | undefined> {
    try {
      const api = createApiClient(CONFIG_BASE);
      const response = await api.get<never, ApiResponse<SystemConfig>>(`/${encodeURIComponent(key)}`);
      const config = response?.data;
      if (config && typeof config.value === 'string') {
        return config.value as unknown as T;
      }
    } catch {
      // 配置读取失败时静默降级到 defaultValue
    }
    return defaultValue;
  },

  /** 分页读取配置列表 */
  async list(params?: { pageNum?: number; pageSize?: number; keyword?: string }): Promise<SystemConfig[]> {
    const api = createApiClient(CONFIG_BASE);
    const response = await api.get<never, ApiResponse<{ list: SystemConfig[] }>>('', { params });
    return response?.data?.list ?? [];
  },

  /** 按 key 设置配置 */
  async set(key: string, value: string): Promise<void> {
    const api = createApiClient(CONFIG_BASE);
    await api.post<never, ApiResponse<void>>('', { key, value });
  },
};
