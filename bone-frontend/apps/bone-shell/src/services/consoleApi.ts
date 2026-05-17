import axios from 'axios';

export type ConsoleOverview = {
  services?: Array<{ name?: string; serviceCode?: string; status?: string }>;
  resourceUsage?: Record<string, number>;
  keyMetrics?: Record<string, number>;
  updatedAt?: string;
};

export type QuickAction = {
  id: string;
  title: string;
  path: string;
  icon?: string;
};

type BoneApiResponse<T> = { code?: number; success?: boolean; data: T };

function isOk<T>(body: BoneApiResponse<T>): boolean {
  return body.code === 200 || body.success === true;
}

export async function fetchConsoleOverview(): Promise<ConsoleOverview | null> {
  const { data } = await axios.get<BoneApiResponse<ConsoleOverview>>('/api/v1/console/overview');
  return isOk(data) ? data.data : null;
}

export async function fetchQuickActions(): Promise<QuickAction[]> {
  const { data } = await axios.get<BoneApiResponse<QuickAction[]>>('/api/v1/console/quick-actions');
  return isOk(data) ? data.data ?? [] : [];
}
