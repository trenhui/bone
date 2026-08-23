import type { NotificationSummary } from '@bone/shared-types';
import { createApiClient } from './apiClient';

const NOTIFY_BASE = '/api/v1/notifications';

/**
 * 通知服务（纯 axios，D1）。
 * 未读计数接口契约由 notification 模块落地；本 change 先定义调用 + fallback 0。
 */
export const notificationService = {
  /** 未读消息数：GET /api/v1/notifications/unread/count，失败回退 0 */
  async getUnreadCount(): Promise<number> {
    try {
      const api = createApiClient(NOTIFY_BASE);
      const res = await api.get<never, NotificationSummary>('/unread/count');
      return res?.unread ?? 0;
    } catch {
      return 0;
    }
  },
};
