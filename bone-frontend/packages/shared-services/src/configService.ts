import type { ApiResponse, SystemConfig } from '@bone/shared-types';
import { createApiClient } from './apiClient';

/**
 * 后端配置控制器实际挂载在 `/api/v1/system/config`（单数），
 * 分页在 `/config/page`，按 key 读取在 `/config/key/{key}`。
 * 与 `bone-system` 的 ConfigController 保持一致，避免前端调用 404。
 */
const CONFIG_BASE = '/api/v1/system/config';

/**
 * 系统配置服务（纯 axios）。
 * 读取后端系统配置；未命中时返回调用方提供的 defaultValue 兜底。
 */
export const configService = {
  /** 按 key 读取配置，缺失时返回 defaultValue */
  async get<T = string>(key: string, defaultValue?: T): Promise<T | undefined> {
    try {
      const api = createApiClient(CONFIG_BASE);
      const response = await api.get<never, ApiResponse<SystemConfig>>(
        `/key/${encodeURIComponent(key)}`,
      );
      const config = response?.data;
      // 后端響应字段为 configValue（见 ConfigResp），非 value
      if (config && typeof config.configValue === 'string') {
        return config.configValue as unknown as T;
      }
    } catch {
      // 配置读取失败时静默降级到 defaultValue
    }
    return defaultValue;
  },

  /** 分页读取配置列表（对应 GET /config/page） */
  async list(params?: { page?: number; size?: number; keyword?: string }): Promise<SystemConfig[]> {
    const api = createApiClient(CONFIG_BASE);
    const response = await api.get<never, ApiResponse<{ records?: SystemConfig[] }>>('/page', {
      params,
    });
    return response?.data?.records ?? [];
  },

  /** 按 key 设置配置（对应 POST /config，创建请求体字段为 configKey/configValue/configType） */
  async set(key: string, value: string, configType = 'SYSTEM'): Promise<void> {
    const api = createApiClient(CONFIG_BASE);
    await api.post<never, ApiResponse<void>>('', { configKey: key, configValue: value, configType });
  },
};
