import type { ApiResponse } from '@bone/shared-types';
import { createApiClient } from './apiClient';

const NOTIFY_BASE = '/api/v1/notification';

/** 站内信 DTO（对齐 NotificationMessage 序列化字段） */
export interface NotificationDTO {
  id: number;
  userId: number;
  title?: string;
  content?: string;
  level?: string;
  read: boolean;
  createdAt?: string;
}

/**
 * 通知服务（纯 axios，D1）。
 * 未读计数复用 notification 模块后端：GET /api/v1/notification/messages/unread-count?userId=
 * 该端点由宿主 bone-system 暴露（SDK 模式，网关转发到 8083），失败回退 0。
 */
export const notificationService = {
  /** 未读消息数；userId 缺省时回退 0（避免无意义请求） */
  async getUnreadCount(userId?: number): Promise<number> {
    if (!userId) return 0;
    try {
      const api = createApiClient(NOTIFY_BASE);
      const res = await api.get<never, ApiResponse<number>>('/messages/unread-count', {
        params: { userId },
      });
      return typeof res?.data === 'number' ? res.data : 0;
    } catch {
      return 0;
    }
  },

  /** 拉取某用户的站内信列表（默认 50 条，按时间倒序由后端保证） */
  async getMessages(userId: number, limit = 50): Promise<NotificationDTO[]> {
    try {
      const api = createApiClient(NOTIFY_BASE);
      const res = await api.get<never, ApiResponse<NotificationDTO[]>>('/messages', {
        params: { userId, limit },
      });
      return Array.isArray(res?.data) ? res.data : [];
    } catch {
      return [];
    }
  },

  /** 标记单条站内信为已读 */
  async markRead(id: number): Promise<void> {
    const api = createApiClient(NOTIFY_BASE);
    await api.post<never, ApiResponse<void>>(`/messages/${id}/read`);
  },
};
