import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { dataSourceApi, pageRecords } from '../services/api';
import type { DataSource } from '../services/types';

/**
 * 数据源列表（Server State → React Query）。
 * 替代原先塞在 Zustand 中的 dataSources，统一管理缓存/失效。
 */
export function useDataSources(): UseQueryResult<DataSource[]> {
  return useQuery<DataSource[]>({
    queryKey: ['dataSources'],
    queryFn: async () =>
      pageRecords((await dataSourceApi.getList({ page: 1, size: 100 })).data),
  });
}
