import { QueryClient } from '@tanstack/react-query';

/**
 * 创建预配置的 QueryClient
 *
 * 各微应用在 main.tsx 中使用：
 * ```tsx
 * import { createQueryClient } from '@bone/shared-services';
 * import { QueryClientProvider } from '@tanstack/react-query';
 *
 * const queryClient = createQueryClient();
 *
 * <QueryClientProvider client={queryClient}>
 *   <App />
 * </QueryClientProvider>
 * ```
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        refetchOnWindowFocus: false,
        retry: 1,
        staleTime: 30_000,
        gcTime: 5 * 60_000,
      },
      mutations: {
        retry: 0,
      },
    },
  });
}

/**
 * React Query 查询键命名空间辅助函数
 * 避免不同微应用的 query key 冲突
 *
 * ```ts
 * const keys = createQueryKeys('iam', ['accounts', 'roles']);
 * // keys.accounts -> ['iam', 'accounts']
 * // keys.accountsDetail -> ['iam', 'accounts', 'detail']
 * ```
 */
export function createQueryKeys(scope: string, domains: string[]) {
  const keys: Record<string, readonly string[]> = {};
  for (const domain of domains) {
    keys[domain] = [scope, domain] as const;
    keys[`${domain}Detail`] = [scope, domain, 'detail'] as const;
  }
  return keys;
}
