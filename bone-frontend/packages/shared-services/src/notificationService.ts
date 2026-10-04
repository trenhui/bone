import type { ApiResponse } from '@bone/shared-types';
import { createApiClient } from './apiClient';

const NOTIFY_BASE = '/api/v1/notification';

/** 站内信 DTO（对齐 NotificationMessage 序列化字段） */
export interface NotificationDTO {
  id: string;
  userId: string;
  title?: string;
  content?: string;
  level?: string;
  read: boolean;
  createdAt?: string;
}

/**
 * 通知服务（纯 axios，D1）。
 *
 * 未读计数复用 notification 模块后端：GET /api/v1/notification/messages/unread-count
 * 该端点由宿主 bone-system 暴露（SDK 模式，网关转发到 8083），失败回退 0。
 *
 * **所有方法都不再接受 userId（2026-10-03 修正 IDOR）**：后端原先把 `userId` 作为
 * `@RequestParam` 接收，而服务层的归属校验正是拿这个入参与库里比对 —— 调用方传自己的
 * ID 就能读/标记他人的站内信。现后端一律从 JWT 主体解析，前端传该参数会被忽略，
 * 留着它只会让人误以为"能指定用户"，故从签名里一并删除。
 */
export const notificationService = {
  /** 未读消息数；失败回退 0（不打断 shell 渲染） */
  async getUnreadCount(): Promise<number> {
    try {
      const api = createApiClient(NOTIFY_BASE);
      const res = await api.get<never, ApiResponse<number>>('/messages/unread-count');
      return typeof res?.data === 'number' ? res.data : 0;
    } catch {
      return 0;
    }
  },

  /** 拉取当前登录用户的站内信（默认 50 条，按时间倒序由后端保证） */
  async getMessages(limit = 50): Promise<NotificationDTO[]> {
    try {
      const api = createApiClient(NOTIFY_BASE);
      const res = await api.get<never, ApiResponse<NotificationDTO[]>>('/messages', {
        params: { limit },
      });
      return Array.isArray(res?.data) ? res.data : [];
    } catch {
      return [];
    }
  },

  /** 标记单条站内信为已读（后端做租户闭环 + 归属校验，归属基准是 JWT 主体） */
  async markRead(id: string): Promise<void> {
    const api = createApiClient(NOTIFY_BASE);
    await api.post<never, ApiResponse<void>>(`/messages/${id}/read`);
  },
};